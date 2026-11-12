package com.biorate.hygen.lang.injection

import com.biorate.hygen.lang.HygenLanguage
import com.biorate.hygen.lang.HygenTokenTypes
import com.biorate.hygen.lang.psi.HygenBody
import com.biorate.hygen.lang.psi.HygenEjsIsland
import com.biorate.hygen.lang.psi.HygenFile
import com.intellij.lang.Language
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assume.assumeTrue
import java.io.File

/**
 * Tests for [HygenEjsIslandInjector] (spec: JavaScript injection into EJS islands).
 *
 * The injector is driven directly through a recording [MultiHostRegistrar], the same harness the
 * sibling body-injection test uses: this exercises the exact contract the platform injection engine
 * consumes — the injected language and the text range handed to [MultiHostRegistrar.addPlace] —
 * without relying on the editor/commit plumbing. Fixtures are built with
 * [PsiFileFactory.createFileFromText] so the island PSI is constructed.
 */
class HygenEjsIslandInjectorTest : BasePlatformTestCase() {

    private fun configure(text: String, name: String = "x.ejs.t"): HygenFile =
        PsiFileFactory.getInstance(project).createFileFromText(name, HygenLanguage, text) as HygenFile

    /** Drives the injector over the single island and records injected language + range. */
    private fun injectIsland(file: HygenFile): Record {
        val island = PsiTreeUtil.findChildOfType(file, HygenEjsIsland::class.java)
            ?: error("no EJS island in ${file.text}")
        return Record().also { record ->
            HygenEjsIslandInjector().getLanguagesToInject(record.registrar, island)
            record.host = island
        }
    }

    fun testExpressionGetsJsInjected() {
        // spec scenario: `<%= h.changeCase.param(name) %>` -> the island body is JS.
        val file = configure("---\nto: app/x.js\n---\nconst a = <%= h.changeCase.param(name) %>;\n")
        val record = injectIsland(file)
        assertEquals("JavaScript", record.languageId)
        // The injection is plain (the synthetic-declaration resolution is provided by the reference
        // contributor, design.md D4 fallback, not by text placed in the injected fragment).
        assertEquals(listOf("h.changeCase.param(name)"), record.places.map { it.codeText })
        // The injection host is the HygenEjsIsland composite.
        assertEquals(HygenTokenTypes.EJS_ISLAND, record.places.single().host!!.node.elementType)
    }

    fun testScriptletGetsJsInjected() {
        // `<% %>` scriptlet without a tag marker gets JS injected too.
        val file = configure("---\nto: app/x.js\n---\n<% ROOT = 'x' %>\n")
        val record = injectIsland(file)
        assertEquals("JavaScript", record.languageId)
        assertEquals(listOf("ROOT = 'x'"), record.places.map { it.codeText })
    }

    fun testTrimMarkersExcluded() {
        // `<%_ ... _%>`: the `_` trim markers on each side must not be part of the JS range.
        val file = configure("---\nto: app/x.js\n---\n<%_ ROOT = 'x' _%>\n")
        val record = injectIsland(file)
        assertEquals("JavaScript", record.languageId)
        assertEquals(listOf("ROOT = 'x'"), record.places.map { it.codeText })
    }

    fun testMinusTrimMarkersExcluded() {
        // `<%- ... -%>`: the `-` markers are absorbed into the delimiter tokens by the lexer.
        val file = configure("---\nto: app/x.js\n---\n<%- ROOT = 'x' -%>\n")
        val record = injectIsland(file)
        assertEquals("JavaScript", record.languageId)
        assertEquals(listOf("ROOT = 'x'"), record.places.map { it.codeText })
    }

    fun testUnterminatedTagHasNoRange() {
        // spec: an unterminated `<%= foo` loses injection (it is not a HygenEjsIsland composite).
        val file = configure("---\nto: app/x.js\n---\nhello <%= foo\n")
        assertNull(PsiTreeUtil.findChildOfType(file, HygenEjsIsland::class.java))
    }

    fun testEscapeHasNoRange() {
        // `<%%` is the literal escape; it is NOT an island, so nothing is injected.
        val file = configure("---\nto: app/x.js\n---\n<%%= name %>\n")
        assertNull(PsiTreeUtil.findChildOfType(file, HygenEjsIsland::class.java))
    }

    fun testEmptyIslandHasNoRange() {
        // `<% %>` is an empty scriptlet: no code region, nothing to inject.
        val file = configure("---\nto: app/x.js\n---\n<%=  %> x\n")
        val record = injectIsland(file)
        assertNull(record.languageId)
        assertEquals(0, record.places.size)
    }

    fun testWhitespaceOnlyIslandHasNoRange() {
        val file = configure("---\nto: app/x.js\n---\nplain <%=   %> text\n")
        val record = injectIsland(file)
        assertNull(record.languageId)
        assertEquals(0, record.places.size)
    }

    fun testCommentIslandHasNoRange() {
        // `<%# ... %>` is an EJS comment, not code: no JavaScript injection.
        val file = configure("---\nto: app/x.js\n---\n<%# not code %>\n")
        val record = injectIsland(file)
        assertNull(record.languageId)
        assertEquals(0, record.places.size)
    }

    fun testIslandCodeRangeTrimsWhitespace() {
        // The code region excludes surrounding whitespace and the delimiters.
        val file = configure("---\nto: app/x.js\n---\n<%=  foo.bar  %>\n")
        val island = PsiTreeUtil.findChildOfType(file, HygenEjsIsland::class.java)!!
        val range = islandCodeRange(island)!!
        assertEquals("foo.bar", island.text.substring(range.startOffset, range.endOffset))
    }

    // ---- corpus overlap sweep ----

    /**
     * Integration check: across every real-world `.ejs.t` template, the ranges the body injector
     * registers and the ranges the island injector registers never overlap. The body injector only
     * touches [HygenBody] plain-text runs and the island injector only the code region of EJS
     * islands, so overlap would signal a host/range bug (e.g. an island leaking into a body range).
     *
     * Skips gracefully (JUnit `assumeTrue`) when the corpus directory is not present on disk.
     */
    fun testCorpusBodyAndIslandRangesNeverOverlap() {
        val root = File("/www/biorate/application-template/_templates")
        assumeTrue("corpus not present on disk; skipping overlap sweep", root.isDirectory)
        val templates = root.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".ejs.t") }
            .sortedBy { it.path }
            .toList()
        assertTrue("no .ejs.t templates under ${root.path}", templates.isNotEmpty())

        for (template in templates) {
            val file = PsiFileFactory.getInstance(project)
                .createFileFromText(template.name, HygenLanguage, template.readText()) as HygenFile

            val body = PsiTreeUtil.findChildOfType(file, HygenBody::class.java)
            val bodyRanges = body?.let { bodyTextRanges(it).map { r -> r.shiftRight(it.textRange.startOffset) } }
                ?: emptyList()

            val islandRanges = PsiTreeUtil.findChildrenOfType(file, HygenEjsIsland::class.java)
                .flatMap { island ->
                    islandCodeRange(island)?.let {
                        listOf(it.shiftRight(island.textRange.startOffset))
                    } ?: emptyList()
                }

            assertNoOverlap(bodyRanges, islandRanges, template.path)
        }
    }

    private fun assertNoOverlap(
        bodyRanges: List<TextRange>,
        islandRanges: List<TextRange>,
        path: String,
    ) {
        val all = (bodyRanges.map { it to "body" } + islandRanges.map { it to "island" })
            .sortedBy { it.first.startOffset }
        for (i in 1 until all.size) {
            val prev = all[i - 1]
            val cur = all[i]
            if (prev.first.endOffset > cur.first.startOffset) {
                fail(
                    "overlap in $path: ${prev.second} ${prev.first} vs ${cur.second} ${cur.first}",
                )
            }
        }
    }

    /** Records the injection the injector produces: host language + injected ranges. */
    private class Record {
        var languageId: String? = null
        var host: PsiLanguageInjectionHost? = null
        val places = mutableListOf<Place>()

        val registrar: MultiHostRegistrar
            get() = object : MultiHostRegistrar {
                override fun startInjecting(language: Language): MultiHostRegistrar {
                    this@Record.languageId = language.id
                    return this
                }

                override fun addPlace(
                    prefix: String?,
                    suffix: String?,
                    host: PsiLanguageInjectionHost,
                    rangeWithinHost: TextRange,
                ): MultiHostRegistrar {
                    places += Place(
                        host.text.substring(rangeWithinHost.startOffset, rangeWithinHost.endOffset),
                        host,
                        prefix,
                        suffix,
                    )
                    return this
                }

                override fun doneInjecting() = Unit
            }

        data class Place(
            val codeText: String,
            val host: PsiLanguageInjectionHost?,
            val prefix: String?,
            val suffix: String?,
        ) {
            /** The full text the injection engine materializes: prefix + code + suffix. */
            val fullText: String
                get() = (prefix ?: "") + codeText + (suffix ?: "")
        }
    }
}
