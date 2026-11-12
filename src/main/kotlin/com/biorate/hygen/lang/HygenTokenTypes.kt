package com.biorate.hygen.lang

import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet

/** AST element types of the hygen template language. */
object HygenTokenTypes {
    // ---- frontmatter ----
    val FRONTMATTER_DELIMITER = HygenElementType("HYGEN_FRONTMATTER_DELIMITER")
    val FRONTMATTER_KEY = HygenElementType("HYGEN_FRONTMATTER_KEY")
    val FRONTMATTER_COLON = HygenElementType("HYGEN_FRONTMATTER_COLON")
    val FRONTMATTER_VALUE = HygenElementType("HYGEN_FRONTMATTER_VALUE")
    val FRONTMATTER_COMMENT = HygenElementType("HYGEN_FRONTMATTER_COMMENT")
    val FRONTMATTER_WHITESPACE = HygenElementType("HYGEN_FRONTMATTER_WHITESPACE")

    // ---- EJS islands (shared by frontmatter and body) ----
    val EJS_OPEN_DELIMITER = HygenElementType("HYGEN_EJS_OPEN_DELIMITER")
    val EJS_CLOSE_DELIMITER = HygenElementType("HYGEN_EJS_CLOSE_DELIMITER")
    val EJS_EXPRESSION = HygenElementType("HYGEN_EJS_EXPRESSION")
    val EJS_SCRIPTLET = HygenElementType("HYGEN_EJS_SCRIPTLET")
    val EJS_COMMENT = HygenElementType("HYGEN_EJS_COMMENT")
    val EJS_ESCAPE = HygenElementType("HYGEN_EJS_ESCAPE")
    val EJS_UNTERMINATED = HygenElementType("HYGEN_EJS_UNTERMINATED")

    // ---- body ----
    val BODY_TEXT = HygenElementType("HYGEN_BODY_TEXT")

    // ---- PSI nodes ----
    val FRONTMATTER = HygenElementType("HYGEN_FRONTMATTER")
    val FRONTMATTER_KEY_VALUE = HygenElementType("HYGEN_FRONTMATTER_KEY_VALUE")
    val EJS_ISLAND = HygenElementType("HYGEN_EJS_ISLAND")
    val BODY = HygenElementType("HYGEN_BODY")
    val UNTERMINATED_EJS = HygenElementType("HYGEN_UNTERMINATED_EJS")

    val WHITESPACE_TOKENS: TokenSet = TokenSet.create(FRONTMATTER_WHITESPACE)
    val COMMENT_TOKENS: TokenSet = TokenSet.create(FRONTMATTER_COMMENT, EJS_COMMENT)
    val STRING_LITERALS: TokenSet = TokenSet.EMPTY
}

class HygenElementType(debugName: String) : IElementType(debugName, HygenLanguage)
