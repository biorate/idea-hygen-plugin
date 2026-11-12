package com.biorate.hygen.lang.injection

import com.biorate.hygen.lang.HygenTokenTypes
import com.biorate.hygen.lang.psi.HygenEjsIsland
import com.intellij.lang.Language
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

/**
 * Injects JavaScript into the code region of an EJS island (spec: JavaScript injection into EJS
 * islands).
 *
 * Complement to [HygenMultiHostInjector]: while that injector highlights the plain body text in
 * the detected host language, EJS islands carry EJS code — a `<%= %>` expression or a `<% %>`
 * scriptlet — which is always JavaScript. This injector registers for the [HygenEjsIsland]
 * composite (the element type the platform queries, which implements
 * [com.intellij.psi.PsiLanguageInjectionHost]) and injects JavaScript exactly into the code region
 * between the opening delimiter (`<%`, `<%=`, `<%-`, `<%_`) and the closing one (`%>`, `-%>`,
 * `_%>`), excluding both delimiters and the `_`/`-` trim markers.
 *
 * The lexer absorbs a `-` trim marker into the delimiter token (so `<%-` and `-%>` are already a
 * delimiter), but a `_` trim marker (`<%_` / `_%>`) is classified as part of the code region; the
 * range logic strips it. Leading/trailing whitespace around the actual code is trimmed, so an empty
 * or whitespace-only island produces no injection.
 *
 * Degrades gracefully (spec: graceful degradation): a missing JavaScript language, an unterminated
 * island, an EJS comment (`<%# %>`), an empty island and an `EJS_ESCAPE` all simply result in no
 * injection, and the method never throws. The JavaScript language is looked up by id — the JS PSI
 * lives in a bundled plugin that may be absent at runtime — mirroring
 * [com.biorate.hygen.context.JavaScriptConfigParser].
 */
class HygenEjsIslandInjector : MultiHostInjector {

    override fun getLanguagesToInject(registrar: MultiHostRegistrar, host: PsiElement) {
        val island = host as? HygenEjsIsland ?: return
        val language: Language = Language.findLanguageByID(JS_LANGUAGE_ID) ?: return
        val range = islandCodeRange(island) ?: return

        registrar.startInjecting(language)
        registrar.addPlace(null, null, island, range)
        registrar.doneInjecting()
    }

    override fun elementsToInjectIn(): List<Class<out PsiElement>> =
        listOf(HygenEjsIsland::class.java)

    private companion object {
        const val JS_LANGUAGE_ID = "JavaScript"
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
