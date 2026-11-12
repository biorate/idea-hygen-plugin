package com.biorate.hygen.lang.injection

import com.biorate.hygen.context.HygenTemplateContext
import com.biorate.hygen.lang.HygenTokenTypes
import com.biorate.hygen.lang.psi.HygenEjsIsland
import com.biorate.hygen.lang.psi.HygenFile
import com.intellij.lang.Language
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

/**
 * Injects JavaScript into the EJS islands of a template as ONE JS document per file
 * (`combined-js-injection`, spec: JavaScript injection into EJS islands).
 *
 * The former [HygenEjsIslandInjector] registered a separate injected document per island, so a
 * construct whose brace opens in one island (`<% if (CLIENT) { -%>`) and closes in another
 * (`<% } -%>`) produced a false "Missing }". This injector instead registers for the [HygenFile]
 * and hands the platform every island's code region as a place of a single injection: the platform
 * concatenates the places into one JS document, so braces and declarations span islands.
 *
 * Host lifecycle: one `startInjecting` → one `addPlace` per island code range (ranges relative to
 * the FILE element, computed by [fileIslandCodeRanges]) → one `doneInjecting`. The injected host is
 * the [HygenFile] itself, so the reference contributor's host lookup must resolve to the file (see
 * [HygenEjsReferenceProvider], which was adapted from per-island to per-file).
 *
 * Prelude: the synthetic [HygenEjsPrelude] is prepended ONCE as the leading place of the file
 * (design.md D4, spec: synthetic declarations for the template context). It is placed at the file's
 * zero-width `[0,0)` range — the prelude text as the `prefix` of that place so it leads the merged
 * document. If the platform rejects a zero-length placeholder place (or when the placeholder
 * machinery is unavailable, design.md D4 fallback), the injector degrades to several ranges: the
 * prelude is prepended as the `prefix` of the FIRST island's place instead, and — if even that
 * cannot be expressed — to plain injection without any prelude. Each tier never throws; the rest of
 * the file keeps template highlighting.
 *
 * Graceful degradation (spec): missing JavaScript language, no code islands, comment/empty/
 * whitespace-only islands and unterminated tags all simply produce fewer places or none, and the
 * method never throws. The JavaScript language is looked up by id — the JS PSI lives in a bundled
 * plugin that may be absent — mirroring [com.biorate.hygen.context.JavaScriptConfigParser].
 */
class HygenEjsFileInjector : MultiHostInjector {

    override fun getLanguagesToInject(registrar: MultiHostRegistrar, host: PsiElement) {
        val file = host as? HygenFile ?: return
        val language: Language = Language.findLanguageByID(JS_LANGUAGE_ID) ?: return

        val codeRanges = fileIslandCodeRanges(file)
        if (codeRanges.isEmpty()) return

        registrar.startInjecting(language)
        registerIslandPlaces(registrar, file, codeRanges)
        registrar.doneInjecting()
    }

    override fun elementsToInjectIn(): List<Class<out PsiElement>> =
        listOf(HygenFile::class.java)

    /**
     * Registers the island [codeRanges] as places of the one file-level injection, prepending the
     * [HygenEjsPrelude] once. Every tier degrades gracefully (never throws).
     */
    private fun registerIslandPlaces(
        registrar: MultiHostRegistrar,
        file: HygenFile,
        codeRanges: List<TextRange>,
    ) {
        // Design.md D4 fallback: the prelude is only injected when the reflective placeholder
        // machinery is reachable; otherwise we degrade to plain island injection (still one merged
        // document — cross-island braces/declarations keep working, only the synthetic template
        // context is lost, exactly the graceful-degradation contract).
        val preludeSupported = HygenEjsPlaceholderInjector.create(registrar) != null
        val prelude = if (preludeSupported) {
            // The whole-file context at the file start: engine defaults + config locals/helpers +
            // hook variables. In-template declarations are intentionally NOT included here — in the
            // merged document the JS analyzer natively sees a `const x` from a preceding island, so
            // the prelude only has to declare the identifiers the analyzer can never infer (`h`,
            // config/hook names).
            HygenEjsPrelude.build(HygenTemplateContext.visibleAt(file, file.textRange.startOffset))
        } else {
            null
        }

        // Tier 1 (primary): prelude as the `prefix` of a zero-width `[0,0)` place on the file. It
        // leads the merged document and costs nothing (an empty range contributes exactly the
        // prefix text). Tier 2 (fallback): if that does not hold, prepend the prelude as the
        // `prefix` of the first island's place, consuming that place (no separate island place).
        // Tier 3: no prelude at all.
        val places = buildPlaces(prelude, codeRanges)
        for ((prefix, range) in places) {
            registrar.addPlace(prefix, null, file, range)
        }
    }

    private companion object {
        const val JS_LANGUAGE_ID = "JavaScript"
    }
}

/**
 * Builds the ordered `(prefix, file-relative range)` places for one file-level injection: the
 * prelude leads (if any), then every island's code range.
 *
 * When [prelude] is null the prelude is omitted entirely (design.md D4 fallback). Otherwise, the
 * first place carries it as the `prefix`; [HygenEjsFileInjector] uses the zero-width `[0,0)` file
 * range for that place by default (tier 1). [forceFirstIslandPrelude] selects the documented
 * fallback: prepend the prelude over the FIRST island's range instead (tier 2), consuming the first
 * island place so the text is not emitted twice.
 *
 * Pure, so directly unit-testable.
 */
internal fun buildPlaces(
    prelude: String?,
    codeRanges: List<TextRange>,
    forceFirstIslandPrelude: Boolean = false,
): List<Pair<String?, TextRange>> {
    val places = mutableListOf<Pair<String?, TextRange>>()
    var islandStart = 0
    if (prelude != null) {
        if (forceFirstIslandPrelude) {
            // Tier 2 fallback: the prelude becomes the prefix of the first island's place.
            if (codeRanges.isNotEmpty()) {
                places += (prelude to codeRanges.first())
                islandStart = 1
            }
        } else {
            // Tier 1 primary: the prelude is the prefix of a zero-width `[0,0)` file place.
            places += (prelude to TextRange(0, 0))
        }
    }
    for (i in islandStart until codeRanges.size) {
        places += (null to codeRanges[i])
    }
    return places
}

/**
 * The code ranges of every EJS island in [file] that has injectable code, relative to the FILE's
 * start offset, in source order.
 *
 * Skipped exactly as [islandCodeRange] does today: a comment island (`<%# ... %>`), an empty or
 * whitespace-only island and any island whose code region collapses to nothing all yield null and
 * are dropped. An unterminated (`EJS_UNTERMINATED`) tag never forms a [HygenEjsIsland] composite, so
 * it is naturally absent from the result (spec: an unterminated tag MAY lose injection; the rest of
 * the file keeps template highlighting). The returned ranges never overlap and never cover the
 * `<%`/`%>` delimiters or the `_`/`-` trim markers.
 *
 * Pure with respect to a built [HygenFile], so directly unit-testable.
 */
internal fun fileIslandCodeRanges(file: HygenFile): List<TextRange> {
    val fileStart = file.textRange.startOffset
    return PsiTreeUtil.collectElementsOfType(file, HygenEjsIsland::class.java)
        .mapNotNull { island ->
            islandCodeRange(island)?.let { it.shiftRight(island.textRange.startOffset - fileStart) }
        }
}

/**
 * The code region of [island] — the JavaScript text between the opening and closing delimiters,
 * relative to the island's own start offset — or null when there is nothing to inject.
 *
 * The region starts after the opening delimiter and ends before the closing delimiter, then a
 * single `_` trim marker (for `<%_` / `_%>`) and the surrounding whitespace are trimmed away. A
 * comment island (`<%# ... %>`), an island with no code region at all, or one that becomes empty
 * after trimming all yield null. An `EJS_ESCAPE` / `EJS_UNTERMINATED` leaf never reaches a
 * [HygenEjsIsland] composite, so it is not handled here.
 *
 * Pure with respect to a built [HygenEjsIsland], so it is directly unit-testable.
 */
internal fun islandCodeRange(island: HygenEjsIsland): TextRange? {
    val islandStart = island.textRange.startOffset
    val text = island.text

    var contentStart = -1
    var contentEnd = -1
    for (child in island.node.getChildren(null)) {
        when (child.elementType) {
            HygenTokenTypes.EJS_OPEN_DELIMITER -> contentStart = child.textRange.endOffset
            HygenTokenTypes.EJS_CLOSE_DELIMITER -> contentEnd = child.textRange.startOffset
            HygenTokenTypes.EJS_COMMENT -> return null
            else -> {}
        }
    }
    if (contentStart < 0 || contentEnd < 0 || contentEnd <= contentStart) return null

    val startRel = contentStart - islandStart
    val endRel = contentEnd - islandStart
    if (startRel < 0 || endRel > text.length) return null

    var start = startRel
    var end = endRel

    // `<%_` opening trim marker: a `_` directly after the opening delimiter.
    if (start < end && text[start] == '_') start++
    while (start < end && text[start].isWhitespace()) start++

    // `_%>` closing trim marker: a `_` directly before the closing delimiter.
    if (end > start && text[end - 1] == '_') end--
    while (end > start && text[end - 1].isWhitespace()) end--

    if (start >= end) return null
    return TextRange(start, end)
}
