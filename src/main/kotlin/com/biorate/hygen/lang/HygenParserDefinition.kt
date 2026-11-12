package com.biorate.hygen.lang

import com.biorate.hygen.lang.psi.HygenBody
import com.biorate.hygen.lang.psi.HygenEjsIsland
import com.biorate.hygen.lang.psi.HygenFile
import com.biorate.hygen.lang.psi.HygenFrontmatterKeyValue
import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet

class HygenParserDefinition : ParserDefinition {

    override fun createLexer(project: Project?): Lexer = HygenLexer()

    override fun createParser(project: Project?): PsiParser = HygenParser()

    override fun getFileNodeType(): IFileElementType = FILE

    override fun getWhitespaceTokens(): TokenSet = HygenTokenTypes.WHITESPACE_TOKENS

    override fun getCommentTokens(): TokenSet = HygenTokenTypes.COMMENT_TOKENS

    override fun getStringLiteralElements(): TokenSet = HygenTokenTypes.STRING_LITERALS

    override fun createElement(node: ASTNode): PsiElement = when (node.elementType) {
        HygenTokenTypes.FRONTMATTER_KEY_VALUE -> HygenFrontmatterKeyValue(node)
        HygenTokenTypes.EJS_ISLAND -> HygenEjsIsland(node)
        HygenTokenTypes.BODY -> HygenBody(node)
        else -> ASTWrapperPsiElement(node)
    }

    override fun createFile(viewProvider: FileViewProvider): PsiFile = HygenFile(viewProvider)

    companion object {
        val FILE: IFileElementType = IFileElementType(HygenLanguage)
    }
}

/** Convenience accessor used by inspections and completion. */
val HygenFile.frontmatterPairs: List<HygenFrontmatterKeyValue>
    get() = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(this, HygenFrontmatterKeyValue::class.java).toList()

/** Raw value of the `to:` frontmatter key, or null when absent. */
val HygenFile.toValue: String?
    get() = frontmatterPairs.firstOrNull { it.key == "to" }?.valueText

/** Element type helper kept for readability in inspections. */
fun IElementType.isEjsIsland(): Boolean = this == HygenTokenTypes.EJS_ISLAND

/**
 * Every EJS tag island that the lexer had to leave unterminated (an opening `<%` with no
 * matching `%>`). The parser marks each run as a [HygenTokenTypes.UNTERMINATED_EJS] node.
 */
val HygenFile.ejsUnterminatedTags: List<PsiElement>
    get() {
        val result = mutableListOf<PsiElement>()
        fun walk(el: PsiElement) {
            if (el.node?.elementType == HygenTokenTypes.UNTERMINATED_EJS) result += el
            var child = el.firstChild
            while (child != null) {
                walk(child)
                child = child.nextSibling
            }
        }
        walk(this)
        return result
    }

/**
 * The file starts with a `---` delimiter line but has no matching closing `---` line
 * (spec: broken frontmatter delimiter). The lexer classifies such a run as body text, so there
 * is no dedicated PSI node; the inspection uses this text-level check line by line.
 */
val HygenFile.hasUnterminatedFrontmatter: Boolean
    get() = com.biorate.hygen.lang.HygenTokenizer.hasUnterminatedFrontmatter(text)
