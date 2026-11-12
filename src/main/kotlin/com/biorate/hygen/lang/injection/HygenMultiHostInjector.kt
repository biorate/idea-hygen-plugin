package com.biorate.hygen.lang.injection

import com.biorate.hygen.lang.HygenHostDetection
import com.biorate.hygen.lang.HygenTokenTypes
import com.biorate.hygen.lang.toValue
import com.biorate.hygen.lang.psi.HygenBody
import com.biorate.hygen.lang.psi.HygenFile
import com.intellij.lang.Language
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

/**
 * Injects the host language into the body of a hygen template (design.md D3).
 *
 * The plugin's own PSI covers the frontmatter structure and the EJS islands; the ordinary body
 * text between the islands is highlighted by the real host language through the injection API
 * rather than a composite PSI. The host language is derived from the raw `to:` frontmatter value
 * (or, failing that, the conventional `.ejs.t` file name) via [HygenHostDetection.detectHost].
 *
 * The injector registers for the [HygenBody] composite — the element type that the platform
 * queries for an injected fragment, and which implements [com.intellij.psi.PsiLanguageInjectionHost]
 * — and injects exactly the plain-text runs the lexer classified as [HygenTokenTypes.BODY_TEXT].
 * EJS islands are wrapped in their own composite nodes and never appear among those runs, so the
 * injected host language never covers a `<% %>` tag (the hygen highlighter keeps that). The
 * frontmatter lives outside the body and is never injected.
 *
 * Degrades gracefully: a missing/unsupported host language ([HygenHostDetection] returns null), an
 * empty body or a missing `to:` value all simply result in no injection, and the method never
 * throws.
 */
class HygenMultiHostInjector : MultiHostInjector {

    override fun getLanguagesToInject(registrar: MultiHostRegistrar, host: PsiElement) {
        val body = host as? HygenBody ?: return
        val file = body.containingFile as? HygenFile ?: return

        val language: Language = HygenHostDetection.detectHost(file.toValue, file.name) ?: return

        val ranges = bodyTextRanges(body)
        if (ranges.isEmpty()) return

        registrar.startInjecting(language)
        for (range in ranges) {
            registrar.addPlace(null, null, body, range)
        }
        registrar.doneInjecting()
    }

    override fun elementsToInjectIn(): List<Class<out PsiElement>> =
        listOf(HygenBody::class.java)
}

/**
 * Plain-text ranges of [body] that should be highlighted in the host language, relative to the
 * body's own start offset. Every leaf the lexer classified as [HygenTokenTypes.BODY_TEXT] is
 * included; EJS islands are wrapped in their own composite nodes and are never [BODY_TEXT]
 * leaves, so they are excluded automatically.
 *
 * Pure with respect to a built [HygenBody], so it is directly unit-testable.
 */
internal fun bodyTextRanges(body: HygenBody): List<TextRange> {
    val bodyStart = body.textRange.startOffset
    return body.node.getChildren(null)
        .filter { it.elementType == HygenTokenTypes.BODY_TEXT }
        .map { it.textRange.shiftLeft(bodyStart) }
        .filter { !it.isEmpty }
}
