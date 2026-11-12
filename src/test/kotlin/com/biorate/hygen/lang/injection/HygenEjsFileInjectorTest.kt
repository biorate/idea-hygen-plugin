package com.biorate.hygen.lang.injection

import com.biorate.hygen.lang.HygenLanguage
import com.biorate.hygen.lang.psi.HygenBody
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
 * Tests for [HygenEjsFileInjector] (`combined-js-injection`, spec: JavaScript injection into EJS
 * islands).
 *
 * The injector is driven directly through a recording [MultiHostRegistrar], the same harness the
 * sibling body-injection test uses: this exercises the exact contract the platform injection engine
 * consumes — the injected language, the FILE host, the number of injections and the text ranges
 * handed to [MultiHostRegistrar.addPlace] — without relying on the editor/commit plumbing needed to
 * materialize an injected document for an in-memory [HygenFile]. The end-to-end injected-document
 * rendering (braces spanning islands parsing as one JS document) is left for the sandbox visual
 * check.
 *
 * The central assertion for the one-document contract is [Record.injectionCount] (== 1) together
 * with [Record.fullText] (the text the engine would concatenate) — a single injection with every
 * island's code range is exactly how the platform builds one merged JS document.
 */
class HygenEjsFileInjectorTest : BasePlatformTestCase() {

    private fun configure(text: String, name: String = "x.ejs.t"): HygenFile =
        PsiFileFactory.getInstance(project).createFileFromText(name, HygenLanguage, text) as HygenFile

    /** Drives the injector over the whole file and records the injection(s) it produces. */
    private fun inject(file: HygenFile): Record = Record().also { record ->
        HygenEjsFileInjector().getLanguagesToInject(record.registrar, file)
    }

    fun testRegistersAllIslandsInOneInjection() {
        // Two islands — one injection, host is the file, both code ranges registered as places.
        val file = configure("---\nto: app/x.js\n---\n<%= a %> text <%= b %>\n")
        val record = inject(file)
        assertEquals("JavaScript", record.languageId)
        assertEquals(1, record.injectionCount)
        // One injected document: two island code places in a single start/done lifecycle, host = file.
        assertTrue(record.places.all { it.host == file })
        assertEquals(listOf("a", "b"), record.places.filter { it.codeText.isNotEmpty() }.map { it.codeText })
    }

    fun testBracesSpanningTwoIslandsInOneDocument() {
        // spec: `<% if (CLIENT) { -%>…<% } -%>` — brace opens in one island, closes in another.
        val file = configure("---\nto: app/x.js\n---\n<% if (CLIENT) { -%><%= h.changeCase.param(name) %><% } -%>\n")
        val record = inject(file)
        assertEquals(1, record.injectionCount)
        assertTrue(record.places.any { it.codeText == "if (CLIENT) {" })
        assertTrue(record.places.any { it.codeText == "h.changeCase.param(name)" })
        assertTrue(record.places.any { it.codeText == "}" })
        // The merged document (prelude + every island) has balanced braces — no false "Missing }".
        val full = record.fullText
        assertEquals("merged JS document should be brace-balanced", count(full, "{"), count(full, "}"))
    }

    fun testDeclarationInOneIslandReachableFromLaterIsland() {
        // A `const x` in an early island and a use of `x` in a later island land in the SAME
        // document, so the JS analyzer sees the declaration. The harness cannot materialize JS
        // resolution, but it can pin the merged text the engine would see.
        val file = configure("---\nto: app/x.js\n---\n<% const x = 1; -%>later <%= x %>\n")
        val record = inject(file)
        assertEquals(1, record.injectionCount)
        val full = record.fullText
        val declIndex = full.indexOf("const x = 1;")
        val useIndex = full.lastIndexOf("x")
        assertTrue("declaration should precede use in the merged document", declIndex in 0 until useIndex)
    }

    fun testPreludeOncePerFile() {
        // The synthetic prelude is emitted exactly once (its `h` namespace opens once, not
        // per-island) and it LEADS the single injected document (a prefix on the first place), so a
        // later island's `h.*` reference sees the declarations.
        val file = configure("---\nto: app/x.js\n---\n<%= h.changeCase.param(name) %>\n")
        val record = inject(file)
        assertEquals(1, record.injectionCount)
        assertEquals(1, count(record.fullText, "const h = {"))
        assertNotNull("first place should carry the prelude as a prefix", record.places.first().prefix)
        assertEquals("const h = {", record.places.first().prefix!!.substringBefore("\n"))
    }

    fun testPreludeNotInjectedWhenPlaceholderMachineryUnavailable() {
        // Graceful degradation (design.md D4 fallback): with no placeholder machinery the prelude is
        // dropped but the islands still form one merged document (braces/declarations keep working).
        // This is exercised by forcing the prelude off at the place-builder level.
        val ranges = listOf(TextRange(5, 8))
        val places = buildPlaces(null, ranges)
        assertEquals(1, places.size)
        assertNull(places[0].first)
    }

    fun testEmptyCommentUnterminatedIslandsSkipped() {
        // Empty, whitespace-only, comment and unterminated islands produce no place; the one real
        // island is still registered and the file-level injection still happens.
        val file = configure("---\nto: app/x.js\n---\n<%=  %> <%# comment %> <%= real %> <%= unterminated\n")
        val record = inject(file)
        assertEquals(1, record.injectionCount)
        assertEquals(listOf("real"), record.places.filter { it.codeText.isNotEmpty() }.map { it.codeText })
    }

    fun testNoCodeIslandsNoInjection() {
        // A file whose only islands are comment/empty yields nothing to inject (no start).
        val file = configure("---\nto: app/x.js\n---\nplain <%# c %> text\n")
        val record = inject(file)
        assertNull(record.languageId)
        assertEquals(0, record.places.size)
    }

    fun testNoIslandsAtAllNoInjection() {
        val file = configure("---\nto: app/x.js\n---\njust body text\n")
        val record = inject(file)
        assertNull(record.languageId)
        assertEquals(0, record.injectionCount)
    }

    fun testFileHostIsInjectionHost() {
        // The injected host is the HygenFile (not the island) — the reference contributor relies on
        // `findInjectionHost` returning the file now. The places are the prelude leading place + the
        // single island code place, all hosted on the file.
        val file = configure("---\nto: app/x.js\n---\n<%= h.name %>\n")
        val record = inject(file)
        assertEquals(2, record.places.size)
        assertTrue(record.places.all { it.host == file })
    }

    fun testElementsToInjectInReturnsFile() {
        assertEquals(listOf(HygenFile::class.java), HygenEjsFileInjector().elementsToInjectIn())
    }

    fun testFallbackPreludeOverFirstIslandConsumesIt() {
        // The zero-width `[0,0)` prelude place is rejected/unsupported → the prelude is prepended
        // over the FIRST island's range and that island is not re-added (no double emission).
        val ranges = listOf(TextRange(6, 9), TextRange(20, 23))
        val places = buildPlaces("PRELUDE\n", ranges, forceFirstIslandPrelude = true)
        assertEquals("PRELUDE\n", places[0].first)
        assertEquals(TextRange(6, 9), places[0].second)
        assertEquals(TextRange(20, 23), places[1].second)
        assertEquals(2, places.size)
    }

    fun testFileIsolation() {
        // Each file is injected on its own; the merged text does not bleed between files.
        val f1 = configure("---\nto: app/x.js\n---\n<%= one %>\n")
        val f2 = configure("---\nto: app/x.js\n---\n<%= two %>\n")
        val r1 = inject(f1)
        val r2 = inject(f2)
        assertEquals("one", r1.places.single { it.codeText.isNotEmpty() }.codeText)
        assertEquals("two", r2.places.single { it.codeText.isNotEmpty() }.codeText)
        assertFalse(r1.fullText.contains("two"))
        assertFalse(r2.fullText.contains("one"))
    }

    fun testIslandCodeRangesAreFileRelative() {
        // fileIslandCodeRanges returns ranges relative to the FILE start, in source order, only the
        // code regions (delimiters excluded). Substringing the FILE text must yield the code.
        val file = configure("---\nto: app/x.js\n---\nAA<%= a %>BB<%= b %>CC\n")
        val ranges = fileIslandCodeRanges(file)
        assertEquals(
            listOf("a", "b"),
            ranges.map { file.text.substring(it.startOffset, it.endOffset) },
        )
        assertTrue(ranges[0].startOffset < ranges[1].startOffset)
        assertTrue(ranges[0].endOffset <= ranges[1].startOffset)
        assertTrue(ranges.all { it.startOffset >= 0 && it.endOffset <= file.text.length })
    }

    // ---- corpus overlap sweep ----

    /**
     * Integration check: across every real-world `.ejs.t` template, the ranges the body injector
     * registers and the ranges the file EJS injector registers never overlap. The body injector only
     * touches [HygenBody] plain-text runs and the file injector only the code region of EJS islands,
     * so overlap would signal a host/range bug (e.g. an island leaking into a body range).
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

            val islandRanges = fileIslandCodeRanges(file)
                .map { it.shiftRight(file.textRange.startOffset) }

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
                fail("overlap in $path: ${prev.second} ${prev.first} vs ${cur.second} ${cur.first}")
            }
        }
    }

    private fun count(text: String, needle: String): Int {
        var count = 0
        var idx = text.indexOf(needle)
        while (idx >= 0) {
            count++
            idx = text.indexOf(needle, idx + needle.length)
        }
        return count
    }

    /** Records the injection the injector produces: host + language + place ranges/prefixes. */
    private class Record {
        var languageId: String? = null
        var injectionCount = 0
        val places = mutableListOf<Place>()

        val registrar: MultiHostRegistrar
            get() = object : MultiHostRegistrar {
                override fun startInjecting(language: Language): MultiHostRegistrar {
                    languageId = language.id
                    injectionCount++
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

        /** The concatenated text the engine would materialize for the merged JS document. */
        val fullText: String
            get() = places.joinToString("") { it.fullText }

        data class Place(
            val codeText: String,
            val host: PsiLanguageInjectionHost?,
            val prefix: String?,
            val suffix: String?,
        ) {
            val fullText: String
                get() = (prefix ?: "") + codeText + (suffix ?: "")
        }
    }
}
