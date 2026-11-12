package com.biorate.hygen.lang

import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet

/**
 * Builds the template PSI (design.md D2/D3):
 *
 *     HygenFile
 *       HYGEN_FRONTMATTER (two delimiters + key/value pairs)
 *       HYGEN_BODY (text + EJS islands)
 */
class HygenParser : PsiParser {

    override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
        val fileMarker = builder.mark()

        if (isFrontmatter(builder)) {
            parseFrontmatter(builder)
        }
        parseBody(builder)

        while (!builder.eof()) builder.advanceLexer()
        fileMarker.done(root)
        return builder.treeBuilt
    }

    /** True when the first token is a `---` delimiter and another one closes it. */
    private fun isFrontmatter(builder: PsiBuilder): Boolean {
        if (builder.tokenType != HygenTokenTypes.FRONTMATTER_DELIMITER) return false
        val probe = builder.mark()
        builder.advanceLexer()
        var closed = false
        while (!builder.eof()) {
            if (builder.tokenType == HygenTokenTypes.FRONTMATTER_DELIMITER) {
                closed = true
                break
            }
            builder.advanceLexer()
        }
        probe.rollbackTo()
        return closed
    }

    private fun parseFrontmatter(builder: PsiBuilder) {
        val frontmatter = builder.mark()
        // opening delimiter
        builder.advanceLexer()
        while (!builder.eof() && builder.tokenType != HygenTokenTypes.FRONTMATTER_DELIMITER) {
            when (builder.tokenType) {
                HygenTokenTypes.FRONTMATTER_KEY -> parseKeyValue(builder)
                HygenTokenTypes.EJS_OPEN_DELIMITER -> parseEjsIsland(builder)
                HygenTokenTypes.EJS_UNTERMINATED -> parseUnterminatedEjs(builder)
                else -> builder.advanceLexer()
            }
        }
        if (!builder.eof()) builder.advanceLexer() // closing delimiter
        frontmatter.done(HygenTokenTypes.FRONTMATTER)
    }

    private fun parseKeyValue(builder: PsiBuilder) {
        val pair = builder.mark()
        builder.advanceLexer() // key
        if (builder.tokenType == HygenTokenTypes.FRONTMATTER_COLON) builder.advanceLexer()
        while (!builder.eof() && VALUE_TOKENS.contains(builder.tokenType)) {
            if (builder.tokenType == HygenTokenTypes.EJS_OPEN_DELIMITER) parseEjsIsland(builder)
            else if (builder.tokenType == HygenTokenTypes.EJS_UNTERMINATED) parseUnterminatedEjs(builder)
            else builder.advanceLexer()
        }
        pair.done(HygenTokenTypes.FRONTMATTER_KEY_VALUE)
    }

    private fun parseBody(builder: PsiBuilder) {
        val body = builder.mark()
        while (!builder.eof()) {
            when (builder.tokenType) {
                HygenTokenTypes.EJS_OPEN_DELIMITER -> parseEjsIsland(builder)
                HygenTokenTypes.EJS_UNTERMINATED -> parseUnterminatedEjs(builder)
                else -> builder.advanceLexer()
            }
        }
        body.done(HygenTokenTypes.BODY)
    }

    private fun parseEjsIsland(builder: PsiBuilder) {
        val island = builder.mark()
        builder.advanceLexer() // opening delimiter
        while (!builder.eof() && builder.tokenType != HygenTokenTypes.EJS_CLOSE_DELIMITER) {
            builder.advanceLexer()
        }
        if (!builder.eof()) builder.advanceLexer() // closing delimiter
        island.done(HygenTokenTypes.EJS_ISLAND)
    }

    private fun parseUnterminatedEjs(builder: PsiBuilder) {
        val marker = builder.mark()
        builder.advanceLexer()
        marker.done(HygenTokenTypes.UNTERMINATED_EJS)
    }

    private companion object {
        val VALUE_TOKENS: TokenSet = TokenSet.create(
            HygenTokenTypes.FRONTMATTER_VALUE,
            HygenTokenTypes.FRONTMATTER_WHITESPACE,
            HygenTokenTypes.EJS_OPEN_DELIMITER,
            HygenTokenTypes.EJS_EXPRESSION,
            HygenTokenTypes.EJS_SCRIPTLET,
            HygenTokenTypes.EJS_COMMENT,
            HygenTokenTypes.EJS_CLOSE_DELIMITER,
            HygenTokenTypes.EJS_ESCAPE,
            HygenTokenTypes.EJS_UNTERMINATED,
        )
    }
}
