package com.biorate.hygen.lang.highlight

import com.biorate.hygen.lang.HygenTokenTypes
import com.biorate.hygen.lang.HygenTokenizer
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Verifies the highlighting wiring: every colored token maps to its key, uncolored tokens map
 * to nothing, and the color settings page covers and exercises the whole set.
 */
class HygenHighlightingTest : BasePlatformTestCase() {

    fun testTokenToAttributeKeyMapping() {
        val highlighter = HygenSyntaxHighlighter()
        val expected = mapOf(
            HygenTokenTypes.FRONTMATTER_DELIMITER to HygenTextAttributes.FRONTMATTER_DELIMITER,
            HygenTokenTypes.FRONTMATTER_KEY to HygenTextAttributes.FRONTMATTER_KEY,
            HygenTokenTypes.FRONTMATTER_COLON to HygenTextAttributes.FRONTMATTER_COLON,
            HygenTokenTypes.FRONTMATTER_VALUE to HygenTextAttributes.FRONTMATTER_VALUE,
            HygenTokenTypes.FRONTMATTER_COMMENT to HygenTextAttributes.FRONTMATTER_COMMENT,
            HygenTokenTypes.EJS_OPEN_DELIMITER to HygenTextAttributes.EJS_DELIMITER,
            HygenTokenTypes.EJS_CLOSE_DELIMITER to HygenTextAttributes.EJS_DELIMITER,
            HygenTokenTypes.EJS_EXPRESSION to HygenTextAttributes.EJS_EXPRESSION,
            HygenTokenTypes.EJS_SCRIPTLET to HygenTextAttributes.EJS_SCRIPTLET,
            HygenTokenTypes.EJS_COMMENT to HygenTextAttributes.EJS_COMMENT,
            HygenTokenTypes.EJS_ESCAPE to HygenTextAttributes.EJS_ESCAPE,
            HygenTokenTypes.EJS_UNTERMINATED to HygenTextAttributes.EJS_UNTERMINATED,
        )
        for ((token, key) in expected) {
            assertEquals(
                "unexpected attributes for ${token.debugName}",
                listOf(key),
                highlighter.getTokenHighlights(token).toList(),
            )
        }
    }

    fun testUncoloredTokensHaveNoAttributes() {
        val highlighter = HygenSyntaxHighlighter()
        assertTrue(highlighter.getTokenHighlights(HygenTokenTypes.BODY_TEXT).isEmpty())
        assertTrue(highlighter.getTokenHighlights(HygenTokenTypes.FRONTMATTER_WHITESPACE).isEmpty())
    }

    fun testColorSettingsPageCoversEveryAttribute() {
        val page = HygenColorSettingsPage()
        assertEquals("Hygen Template", page.displayName)
        assertNotNull(page.icon)
        assertTrue(page.highlighter is HygenSyntaxHighlighter)
        assertTrue(page.colorDescriptors.isEmpty())

        val described: Set<TextAttributesKey> = page.attributeDescriptors.map { it.key }.toSet()
        for (key in allKeys()) {
            assertTrue("no descriptor for ${key.externalName}", key in described)
        }
    }

    fun testDemoTextExercisesEveryTagFamily() {
        val demo = HygenColorSettingsPage().demoText
        val present = HygenTokenizer.tokenize(demo).map { it.type.debugName }.toSet()
        for (expected in listOf(
            "HYGEN_FRONTMATTER_DELIMITER",
            "HYGEN_FRONTMATTER_KEY",
            "HYGEN_FRONTMATTER_VALUE",
            "HYGEN_FRONTMATTER_COMMENT",
            "HYGEN_EJS_OPEN_DELIMITER",
            "HYGEN_EJS_CLOSE_DELIMITER",
            "HYGEN_EJS_EXPRESSION",
            "HYGEN_EJS_SCRIPTLET",
            "HYGEN_EJS_COMMENT",
            "HYGEN_EJS_ESCAPE",
            "HYGEN_EJS_UNTERMINATED",
        )) {
            assertTrue("demo text is missing $expected", expected in present)
        }
    }

    private fun allKeys(): List<TextAttributesKey> = listOf(
        HygenTextAttributes.FRONTMATTER_DELIMITER,
        HygenTextAttributes.FRONTMATTER_KEY,
        HygenTextAttributes.FRONTMATTER_COLON,
        HygenTextAttributes.FRONTMATTER_VALUE,
        HygenTextAttributes.FRONTMATTER_COMMENT,
        HygenTextAttributes.EJS_DELIMITER,
        HygenTextAttributes.EJS_EXPRESSION,
        HygenTextAttributes.EJS_SCRIPTLET,
        HygenTextAttributes.EJS_COMMENT,
        HygenTextAttributes.EJS_ESCAPE,
        HygenTextAttributes.EJS_UNTERMINATED,
    )
}
