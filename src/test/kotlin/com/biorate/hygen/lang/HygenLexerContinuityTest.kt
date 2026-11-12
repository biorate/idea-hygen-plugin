package com.biorate.hygen.lang

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Regression test (editor flash bug): the IDEA 2025.2 `ValidatingLexerWrapper` rejects any lexer
 * whose token stream is *discontinuous* — every offset in the buffer must be covered by exactly one
 * token, with each token's `start == previous.end` and the stream ending at the buffer end. When
 * the `HygenLexer` skipped a character (the newline(s) trailing the `---` frontmatter delimiters)
 * without emitting a token, the editor highlighter flashed open/closed on real template files.
 *
 * This test asserts continuity over two corpora:
 *  - a set of hardcoded fixtures covering the tricky shapes (frontmatter+body, empty frontmatter,
 *    CRLF line endings, blank lines, comments, EJS of every family, unterminated tags, files with
 *    and without frontmatter);
 *  - every file under the real hygen template tree used by the host application
 *    (`/www/biorate/application-template/_templates`), read from disk. That directory is skipped
 *    gracefully (JUnit `assumeTrue`) when it is not present on this machine, so the check cannot
 *    fail a build that runs without the sibling repo.
 *
 * Both the pure [HygenTokenizer] (the single source of truth) and the platform-facing [HygenLexer]
 * (the stream `ValidatingLexerWrapper` validates) are exercised.
 */
class HygenLexerContinuityTest {

    // ---- hardcoded fixtures ----------------------------------------------

    private val fixtures = listOf(
        // frontmatter + body, EJS of several families inside and outside the frontmatter
        "---\nto: app/<%= name %>.js\ninject: true\n---\nclass <%= Name %> {}\n",
        // empty frontmatter: two adjacent `---` lines (the reported reproduction shape)
        "---\n---\n<% ROOT = locals['root']; %>\n",
        // CRLF line endings everywhere
        "---\r\nto: x.js\r\ninject: true\r\n---\r\nbody\r\n",
        // leading/trailing spaces on delimiter/blank lines, comment line, blank lines
        "---\n# a comment\n\nto: x.js\n---\nbody\n",
        // no frontmatter at all (body-only file)
        "plain <%= x %> text\n",
        // unterminated EJS (must still cover every offset through the end of the buffer)
        "hello <%= name",
        // unterminated frontmatter falls back to body text
        "---\nto: x.js\nno closing delimiter\n",
        // trailing newline present/absent variations
        "---\nto: x\n---",
        "---\nto: x\n---\n",
        // no newline at all on a body-only file
        "no newline here",
        // `to:` value that is pure text with a dot and EJS escape `<%%`
        "---\nto: <%% literal %>.ejs.t\n---\nbody\n",
    )

    @Test
    fun fixtureCorporaIsContiguousForTokenizer() {
        for (text in fixtures) {
            assertTokenizerContiguous(text)
        }
    }

    @Test
    fun fixtureCorporaIsContiguousForLexer() {
        for (text in fixtures) {
            assertLexerContiguous(text)
        }
    }

    // ---- real-world template corpus ---------------------------------------

    @Test
    fun realWorldTemplateCorpusIsContiguousForTokenizer() {
        val corpus = corpusRoot()
        assumeTrue(
            "hygen application-template corpus not found on disk; skipping disk-based continuity check",
            corpus != null,
        )
        val files = collectFiles(corpus!!)
        assertTrue("no template files collected under $corpus", files.isNotEmpty())
        for (file in files) {
            val text = file.readText(StandardCharsets.UTF_8)
            assertTokenizerContiguous(text) {
                file.relativeTo(corpus).path
            }
        }
    }

    @Test
    fun realWorldTemplateCorpusIsContiguousForLexer() {
        val corpus = corpusRoot()
        assumeTrue(
            "hygen application-template corpus not found on disk; skipping disk-based continuity check",
            corpus != null,
        )
        val files = collectFiles(corpus!!)
        assertTrue("no template files collected under $corpus", files.isNotEmpty())
        for (file in files) {
            val text = file.readText(StandardCharsets.UTF_8)
            assertLexerContiguous(text) {
                file.relativeTo(corpus).path
            }
        }
    }

    // ---- helpers ----------------------------------------------------------

    /** Root of the real-world template tree, or null when not present on this machine. */
    private fun corpusRoot(): File? {
        listOf("/www/biorate/application-template/_templates").forEach {
            val dir = File(it)
            if (dir.isDirectory) return dir
        }
        return null
    }

    private fun collectFiles(dir: File): List<File> =
        dir.walkTopDown().filter { it.isFile }.sortedBy { it.path }.toList()

    /** Asserts the pure tokenizer emits a contiguous stream covering the entire document. */
    private fun assertTokenizerContiguous(text: String, label: () -> String = { "<fixture>" }) {
        val stream = HygenTokenizer.tokenize(text)
        var expected = 0
        for (t in stream) {
            assertEquals(
                "${label()}: token ${t.type.debugName}[${t.start},${t.end}) starts at ${t.start} but expected $expected",
                expected,
                t.start,
            )
            assertTrue(
                "${label()}: token ${t.type.debugName} at ${t.start} is empty",
                t.end > t.start,
            )
            expected = t.end
        }
        assertEquals(
            "${label()}: token stream ends at $expected but document length is ${text.length}",
            text.length,
            expected,
        )
    }

    /** Asserts the platform-facing [HygenLexer] emits the same contiguous stream. */
    private fun assertLexerContiguous(text: String, label: () -> String = { "<fixture>" }) {
        val lexer = HygenLexer()
        lexer.start(text, 0, text.length, 0)
        var previousEnd = 0
        var count = 0
        while (lexer.tokenType != null) {
            val start = lexer.tokenStart
            val end = lexer.tokenEnd
            assertEquals(
                "${label()}: lexer token ${lexer.tokenType!!.debugName}[$start,$end) starts at $start but expected $previousEnd",
                previousEnd,
                start,
            )
            assertTrue("${label()}: lexer token ${lexer.tokenType!!.debugName} at $start is empty", end > start)
            previousEnd = end
            assertTrue("${label()}: lexer token ${lexer.tokenType!!.debugName} exceeds buffer", end <= text.length)
            count++
            lexer.advance()
        }
        assertEquals("${label()}: lexer stream ended at $previousEnd but document length is ${text.length}", text.length, previousEnd)
        assertTrue("${label()}: lexer produced no tokens for non-empty buffer", count > 0 || text.isEmpty())
    }
}
