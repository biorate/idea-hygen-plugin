package com.biorate.hygen.lang.highlight

import com.biorate.hygen.lang.HygenLexer
import com.biorate.hygen.lang.HygenTokenTypes
import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType

/**
 * Maps the hygen lexer tokens (design.md D2) onto [HygenTextAttributes] keys.
 *
 * The lexer is the same single-state-machine used by the parser, so the highlighting always
 * matches the PSI. Tokens that carry no color (body text, frontmatter whitespace) map to an
 * empty array.
 */
class HygenSyntaxHighlighter : SyntaxHighlighterBase() {

    override fun getHighlightingLexer(): Lexer = HygenLexer()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> =
        TOKEN_KEYS[tokenType] ?: TextAttributesKey.EMPTY_ARRAY

    private companion object {
        val TOKEN_KEYS: Map<IElementType, Array<TextAttributesKey>> = mapOf(
            HygenTokenTypes.FRONTMATTER_DELIMITER to key(HygenTextAttributes.FRONTMATTER_DELIMITER),
            HygenTokenTypes.FRONTMATTER_KEY to key(HygenTextAttributes.FRONTMATTER_KEY),
            HygenTokenTypes.FRONTMATTER_COLON to key(HygenTextAttributes.FRONTMATTER_COLON),
            HygenTokenTypes.FRONTMATTER_VALUE to key(HygenTextAttributes.FRONTMATTER_VALUE),
            HygenTokenTypes.FRONTMATTER_COMMENT to key(HygenTextAttributes.FRONTMATTER_COMMENT),
            HygenTokenTypes.EJS_OPEN_DELIMITER to key(HygenTextAttributes.EJS_DELIMITER),
            HygenTokenTypes.EJS_CLOSE_DELIMITER to key(HygenTextAttributes.EJS_DELIMITER),
            HygenTokenTypes.EJS_EXPRESSION to key(HygenTextAttributes.EJS_EXPRESSION),
            HygenTokenTypes.EJS_SCRIPTLET to key(HygenTextAttributes.EJS_SCRIPTLET),
            HygenTokenTypes.EJS_COMMENT to key(HygenTextAttributes.EJS_COMMENT),
            HygenTokenTypes.EJS_ESCAPE to key(HygenTextAttributes.EJS_ESCAPE),
            HygenTokenTypes.EJS_UNTERMINATED to key(HygenTextAttributes.EJS_UNTERMINATED),
        )

        fun key(attributeKey: TextAttributesKey): Array<TextAttributesKey> = arrayOf(attributeKey)
    }
}
