package com.biorate.hygen.lang.psi

import com.biorate.hygen.lang.HygenLanguage
import com.biorate.hygen.lang.HygenTokenTypes
import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.ASTNode
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import com.intellij.psi.LiteralTextEscaper
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import com.biorate.hygen.lang.HygenFileType

class HygenFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, HygenLanguage) {
    override fun getFileType(): FileType = HygenFileType

    override fun toString(): String = "Hygen template file"
}

/** A frontmatter `key: value` pair; the value may contain EJS islands. */
class HygenFrontmatterKeyValue(node: ASTNode) : ASTWrapperPsiElement(node) {
    val key: String?
        get() = keyElement?.text

    /** The PSI element covering the key text, used as the inspection highlight anchor. */
    val keyElement: PsiElement?
        get() = node.findChildByType(HygenTokenTypes.FRONTMATTER_KEY)?.psi

    /** Raw (un-rendered) value text, with EJS islands kept verbatim. */
    val valueText: String
        get() {
            val builder = StringBuilder()
            var child: PsiElement? = firstChild
            var seenColon = false
            while (child != null) {
                if (child.node.elementType == HygenTokenTypes.FRONTMATTER_COLON) {
                    seenColon = true
                } else if (seenColon && child.node.elementType != HygenTokenTypes.FRONTMATTER_WHITESPACE) {
                    builder.append(child.text)
                }
                child = child.nextSibling
            }
            return builder.toString().trim()
        }
}

/** An EJS island: `<%= %>`, `<%- %>`, `<% %>`, `<%# %>`. */
class HygenEjsIsland(node: ASTNode) : ASTWrapperPsiElement(node)

/**
 * The body of the template (everything after the closing frontmatter delimiter).
 *
 * Implements [PsiLanguageInjectionHost] (design.md D3): the detected host language is injected
 * into the plain-text ranges of this body through the injection API. EJS islands stay under the
 * template lexer/highlighter (they are not part of any injected range), so `<% %>` tags keep
 * template highlighting while the surrounding code is highlighted in the host language.
 */
class HygenBody(node: ASTNode) : ASTWrapperPsiElement(node), PsiLanguageInjectionHost {
    override fun isValidHost(): Boolean = true

    override fun updateText(text: String): PsiLanguageInjectionHost = this

    override fun createLiteralTextEscaper(): LiteralTextEscaper<PsiLanguageInjectionHost> =
        LiteralTextEscaper.createSimple(this)
}
