package com.biorate.hygen.completion

import com.biorate.hygen.catalog.HygenChangeCaseCatalog
import com.biorate.hygen.catalog.HygenFrontmatterCatalog
import com.biorate.hygen.catalog.HygenInflectionCatalog
import com.biorate.hygen.lang.HygenLanguage
import com.biorate.hygen.lang.psi.HygenFile
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Spec-driven tests for the completion logic (spec.md `hygen-completion`, task 6.2).
 *
 * The candidates are produced by the pure [HygenCompletion.computeProposals], the same function
 * the [HygenCompletionProvider] drives, so the tested computation is the production path. Fixtures
 * are built with [PsiFileFactory.createFileFromText] (the same deterministic harness as the other
 * Hygen tests) and the caret is carried by a `<caret>` marker stripped before parsing.
 *
 * Note the assertions use the two-argument [org.junit.Assert.assertEquals] (JUnit 3) form, since
 * [BasePlatformTestCase] extends [junit.framework.TestCase].
 */
class HygenCompletionTest : BasePlatformTestCase() {

    private fun configure(textWithCaret: String): Pair<HygenFile, Int> {
        val idx = textWithCaret.indexOf("<caret>")
        require(idx >= 0) { "fixture must contain a <caret> marker" }
        val clean = textWithCaret.replace("<caret>", "")
        val file = PsiFileFactory.getInstance(project)
            .createFileFromText("hello.ejs.t", HygenLanguage, clean) as HygenFile
        return file to idx
    }

    private fun proposals(textWithCaret: String): List<HygenCompletionCandidate> {
        val (file, caret) = configure(textWithCaret)
        return HygenCompletion.computeProposals(file, caret)
    }

    // ---- spec: Frontmatter key completion ----

    fun testEmptyFrontmatterLineOffersAllKeys() {
        val names = proposals("---\nto: app/x.js\n<caret>\n---\nbody\n").map { it.name }.toSet()
        assertEquals(HygenFrontmatterCatalog.knownKeys, names)
    }

    fun testFrontmatterKeysCarryDescriptions() {
        for (p in proposals("---\nto: app/x.js\n<caret>\n---\nbody\n")) {
            assertTrue("frontmatter key '${p.name}' must carry a description", p.description.isNotBlank())
            assertFalse("frontmatter keys are not namespaces", p.isNamespace)
        }
    }

    fun testPartiallyTypedKeyIsOffered() {
        val names = proposals("---\nto: app/x.js\nin<caret>\n---\nbody\n").map { it.name }.toSet()
        // computeProposals returns every key; the editor narrows by the typed prefix.
        assertEquals(HygenFrontmatterCatalog.knownKeys, names)
    }

    fun testNoKeyCompletionAfterColonInValue() {
        // A caret in a value (after `:`) must not produce key completion.
        assertEquals(0, proposals("---\nto: app/<caret>x.js\n---\nbody\n").size)
    }

    // ---- spec: Helper namespace completion ----

    fun testHDotOffersTopLevelHelpers() {
        val byName = proposals("hello <%= h.<caret> %>\n").associateBy { it.name }
        assertEquals(
            setOf("changeCase", "inflection", "capitalize", "path"),
            byName.keys,
        )
        assertTrue(byName["changeCase"]!!.isNamespace)
        assertTrue(byName["inflection"]!!.isNamespace)
        assertFalse(byName["capitalize"]!!.isNamespace)
        assertFalse(byName["path"]!!.isNamespace)
        for (p in byName.values) assertTrue(p.description.isNotBlank())
    }

    fun testBareHOffersTopLevelHelpers() {
        // `h` with no trailing dot is also accepted as the `h.` entry point.
        val names = proposals("hello <%= h<caret> %>\n").map { it.name }.toSet()
        assertEquals(setOf("changeCase", "inflection", "capitalize", "path"), names)
    }

    // ---- spec: Nested helper member completion ----

    fun testChangeCaseDotOffersAllMembers() {
        val names = proposals("hello <%= h.changeCase.<caret> %>\n").map { it.name }.toSet()
        assertEquals(HygenChangeCaseCatalog.memberNames, names)
    }

    fun testChangeCasePartialMemberIsOffered() {
        val names = proposals("hello <%= h.changeCase.pa<caret> %>\n").map { it.name }.toSet()
        assertEquals(HygenChangeCaseCatalog.memberNames, names)
    }

    fun testChangeCaseMembersCarryDescriptions() {
        for (p in proposals("hello <%= h.changeCase.<caret> %>\n")) {
            assertTrue("changeCase member '${p.name}' must carry a description", p.description.isNotBlank())
            assertFalse("changeCase members are not namespaces", p.isNamespace)
        }
    }

    fun testInflectionDotOffersAllMembers() {
        val names = proposals("hello <%= h.inflection.<caret> %>\n").map { it.name }.toSet()
        assertEquals(HygenInflectionCatalog.memberNames, names)
    }

    fun testInflectionMembersCarryDescriptions() {
        for (p in proposals("hello <%= h.inflection.<caret> %>\n")) {
            assertTrue("inflection member '${p.name}' must carry a description", p.description.isNotBlank())
        }
    }

    // ---- spec: Completion degrades gracefully ----

    fun testUnterminatedTagStillOffersHelperCompletion() {
        // The body has an unterminated `<%`; `h.` completion must still be offered, not dropped.
        val (file, caret) = configure("---\nto: app/x.js\n---\n<%= h.<caret>\n")
        val names = HygenCompletion.computeProposals(file, caret).map { it.name }.toSet()
        assertEquals(setOf("changeCase", "inflection", "capitalize", "path"), names)
    }

    fun testUnterminatedChangeCaseTagStillOffersMembers() {
        val (file, caret) = configure("---\nto: app/x.js\n---\n<%= h.changeCase.<caret>\n")
        val names = HygenCompletion.computeProposals(file, caret).map { it.name }.toSet()
        assertEquals(HygenChangeCaseCatalog.memberNames, names)
    }

    fun testEmptyFileYieldsNothing() {
        val file = PsiFileFactory.getInstance(project)
            .createFileFromText("hello.ejs.t", HygenLanguage, "") as HygenFile
        assertEquals(0, HygenCompletion.computeProposals(file, 0).size)
    }

    // ---- negative: positions that are not completion entry points ----

    fun testPlainBodyTextYieldsNothing() {
        // Caret in body text between islands is not a completion entry point.
        assertEquals(0, proposals("hello <%= h %> <caret>world\n").size)
    }

    fun testUnrelatedPrefixYieldsNothing() {
        assertEquals(0, proposals("hello <%= local.<caret> %>\n").size)
    }
}
