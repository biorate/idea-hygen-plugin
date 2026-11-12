package com.biorate.hygen.lang

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HygenTokenizerTest {

    private fun types(text: String): List<String> =
        HygenTokenizer.tokenize(text).map { it.type.debugName }

    private fun tokenTexts(text: String): List<Pair<String, String>> =
        HygenTokenizer.tokenize(text).map { it.type.debugName to text.substring(it.start, it.end) }

    /** A lexer must cover every offset: each token starts where the previous ended, covering the whole document. */
    private fun assertContiguous(text: String, tokens: List<Pair<String, String>>) {
        val stream = HygenTokenizer.tokenize(text)
        var expected = 0
        for (t in stream) {
            assertEquals("gap: token ${t.type.debugName}[${t.start},${t.end}) starts at ${t.start} but expected $expected", expected, t.start)
            assertEquals("empty token ${t.type.debugName} at ${t.start}", true, t.end > t.start)
            expected = t.end
        }
        assertEquals("trailing gap: stream ends at $expected but document is ${text.length}", text.length, expected)
    }

    @Test
    fun `tokenizes frontmatter and body`() {
        val text = """
            |---
            |to: app/<%= name %>.js
            |inject: true
            |---
            |class <%= Name %> {}
        """.trimMargin()

        val tokens = tokenTexts(text)
        assertContiguous(text, tokens)
        assertEquals(
            listOf(
                "HYGEN_FRONTMATTER_DELIMITER" to "---",
                "HYGEN_FRONTMATTER_WHITESPACE" to "\n",
                "HYGEN_FRONTMATTER_KEY" to "to",
                "HYGEN_FRONTMATTER_COLON" to ":",
                "HYGEN_FRONTMATTER_WHITESPACE" to " ",
                "HYGEN_FRONTMATTER_VALUE" to "app/",
                "HYGEN_EJS_OPEN_DELIMITER" to "<%=",
                "HYGEN_EJS_EXPRESSION" to " name ",
                "HYGEN_EJS_CLOSE_DELIMITER" to "%>",
                "HYGEN_FRONTMATTER_VALUE" to ".js",
                "HYGEN_FRONTMATTER_WHITESPACE" to "\n",
                "HYGEN_FRONTMATTER_KEY" to "inject",
                "HYGEN_FRONTMATTER_COLON" to ":",
                "HYGEN_FRONTMATTER_WHITESPACE" to " ",
                "HYGEN_FRONTMATTER_VALUE" to "true",
                "HYGEN_FRONTMATTER_WHITESPACE" to "\n",
                "HYGEN_FRONTMATTER_DELIMITER" to "---",
            ),
            tokens.take(17),
        )
        assertTrue(tokens.drop(16).any { it.first == "HYGEN_BODY_TEXT" })
        assertTrue(tokens.drop(16).any { it.first == "HYGEN_EJS_EXPRESSION" })
    }

    @Test
    fun `supports all EJS tag families and trimming`() {
        val text = "<%= a %><%- b -%><% c %><%# d %><%% literal %>"
        assertEquals(
            listOf(
                "HYGEN_EJS_OPEN_DELIMITER", "HYGEN_EJS_EXPRESSION", "HYGEN_EJS_CLOSE_DELIMITER",
                "HYGEN_EJS_OPEN_DELIMITER", "HYGEN_EJS_EXPRESSION", "HYGEN_EJS_CLOSE_DELIMITER",
                "HYGEN_EJS_OPEN_DELIMITER", "HYGEN_EJS_SCRIPTLET", "HYGEN_EJS_CLOSE_DELIMITER",
                "HYGEN_EJS_OPEN_DELIMITER", "HYGEN_EJS_COMMENT", "HYGEN_EJS_CLOSE_DELIMITER",
                "HYGEN_EJS_ESCAPE",
                "HYGEN_BODY_TEXT",
            ),
            types(text),
        )
        // the trailing `-` is the trimming marker, not part of the expression
        assertEquals(" b ", tokenTexts(text)[4].second)
        assertEquals("-%>", tokenTexts(text)[5].second)
    }

    @Test
    fun `reports unterminated tag`() {
        val text = "hello <%= name"
        val tokens = tokenTexts(text)
        assertEquals("HYGEN_BODY_TEXT" to "hello ", tokens.first())
        assertEquals("HYGEN_EJS_UNTERMINATED" to "<%= name", tokens.last())
    }

    @Test
    fun `file without frontmatter is body only`() {
        val text = "plain <%= x %> text"
        assertTrue(types(text).none { it.startsWith("HYGEN_FRONTMATTER") })
    }

    @Test
    fun `unclosed frontmatter delimiter falls back to body`() {
        val text = "---\nto: x.js\nbody without closing delimiter"
        assertTrue(types(text).none { it.startsWith("HYGEN_FRONTMATTER") })
    }

    @Test
    fun `frontmatter comments and blank lines`() {
        val text = "---\n# comment\n\nto: x\n---\nbody"
        assertTrue(types(text).contains("HYGEN_FRONTMATTER_COMMENT"))
    }
}
