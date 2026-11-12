package com.biorate.hygen.lang.highlight

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey

/**
 * Editor color keys of the hygen template language.
 *
 * Every key declares a standard platform key as its fallback, so the language is colored out
 * of the box in any scheme (see design.md D2/D3) and users only need to define their own
 * attributes when they want a different color.
 */
object HygenTextAttributes {

    // ---- frontmatter ----
    val FRONTMATTER_DELIMITER = TextAttributesKey.createTextAttributesKey(
        "HYGEN_FRONTMATTER_DELIMITER", DefaultLanguageHighlighterColors.KEYWORD)
    val FRONTMATTER_KEY = TextAttributesKey.createTextAttributesKey(
        "HYGEN_FRONTMATTER_KEY", DefaultLanguageHighlighterColors.INSTANCE_FIELD)
    val FRONTMATTER_COLON = TextAttributesKey.createTextAttributesKey(
        "HYGEN_FRONTMATTER_COLON", DefaultLanguageHighlighterColors.OPERATION_SIGN)
    val FRONTMATTER_VALUE = TextAttributesKey.createTextAttributesKey(
        "HYGEN_FRONTMATTER_VALUE", DefaultLanguageHighlighterColors.STRING)
    val FRONTMATTER_COMMENT = TextAttributesKey.createTextAttributesKey(
        "HYGEN_FRONTMATTER_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT)

    // ---- EJS islands (shared by frontmatter and body) ----
    /** Both the opening `<%`/`<%=`/`<%-`/`<%#` and the closing `%>` delimiter. */
    val EJS_DELIMITER = TextAttributesKey.createTextAttributesKey(
        "HYGEN_EJS_DELIMITER", DefaultLanguageHighlighterColors.KEYWORD)
    val EJS_EXPRESSION = TextAttributesKey.createTextAttributesKey(
        "HYGEN_EJS_EXPRESSION", DefaultLanguageHighlighterColors.CONSTANT)
    val EJS_SCRIPTLET = TextAttributesKey.createTextAttributesKey(
        "HYGEN_EJS_SCRIPTLET", DefaultLanguageHighlighterColors.IDENTIFIER)
    val EJS_COMMENT = TextAttributesKey.createTextAttributesKey(
        "HYGEN_EJS_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT)
    val EJS_ESCAPE = TextAttributesKey.createTextAttributesKey(
        "HYGEN_EJS_ESCAPE", DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE)
    /** A tag that is never closed; the spec colors it as an error construct. */
    val EJS_UNTERMINATED = TextAttributesKey.createTextAttributesKey(
        "HYGEN_EJS_UNTERMINATED", HighlighterColors.BAD_CHARACTER)
}
