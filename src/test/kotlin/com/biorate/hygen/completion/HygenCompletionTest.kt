package com.biorate.hygen.completion

import com.biorate.hygen.catalog.HygenChangeCaseCatalog
import com.biorate.hygen.catalog.HygenFrontmatterCatalog
import com.biorate.hygen.catalog.HygenInflectionCatalog
import com.biorate.hygen.context.ContextSources
import com.biorate.hygen.context.EngineDefaults
import com.biorate.hygen.context.HygenContext
import com.biorate.hygen.context.HygenTemplateContext
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

    private fun proposals(
        textWithCaret: String,
        context: HygenContext,
    ): List<HygenCompletionCandidate> {
        val (file, caret) = configure(textWithCaret)
        return HygenCompletion.computeProposals(file, caret, context)
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

    // ---- spec: context variable completion inside islands (task 2.2) ----

    fun testDefaultsOnlyFileOffersEngineNameVariants() {
        // No config -> engine defaults only: `name` + derived variants + the `h` namespace.
        val names = proposals("hello <%= <caret> %>\n").map { it.name }.toSet()
        assertEquals(setOf("name", "Name", "names", "Names", "h"), names)
    }

    fun testPartialIdentifierOffersNameVariants() {
        // A bare prefix still routes to context-variable completion (engine defaults, no config).
        val names = proposals("hello <%= na<caret> %>\n").map { it.name }.toSet()
        assertEquals(setOf("name", "Name", "names", "Names", "h"), names)
    }

    fun testConfigLocalsBackedFileOffersLocals() {
        // Simulates the context resolved from a `.hygen.js` with localsDefaults (task 2.1 already
        // tests disk resolution; here we assert the locals flow into identifier completion).
        val context = HygenTemplateContext.merge(
            ContextSources(configLocals = setOf("scope", "module"), configHelpers = setOf("root")),
        )
        val names = proposals("hello <%= <caret> %>\n", context).map { it.name }.toSet()
        assertTrue("scope" in names)
        assertTrue("module" in names)
        // The engine defaults are still present alongside the config locals.
        assertTrue(setOf("name", "Name", "names", "Names", "h").all { it in names })
    }

    fun testConfigHelpersMergeIntoHDotCompletion() {
        val context = HygenTemplateContext.merge(
            ContextSources(configHelpers = setOf("root", "stringify", "platform")),
        )
        val names = proposals("hello <%= h.<caret> %>\n", context).map { it.name }.toSet()
        assertEquals(
            setOf("changeCase", "inflection", "capitalize", "path", "root", "stringify", "platform"),
            names,
        )
    }

    fun testConfigHelpersCarryDescriptions() {
        val context = HygenTemplateContext.merge(
            ContextSources(configHelpers = setOf("root", "stringify", "platform")),
        )
        val byName = proposals("hello <%= h.<caret> %>\n", context).associateBy { it.name }
        for (helper in listOf("root", "stringify", "platform")) {
            assertTrue("config helper '$helper' must be documented", byName[helper]!!.description.isNotBlank())
            assertFalse("config helpers are not namespaces", byName[helper]!!.isNamespace)
        }
    }

    // ---- spec: in-template declarations resolve positionally (task 2.2) ----

    fun testDeclarationBelowCaretIsOffered() {
        // Island above declares APP_NAME; the lower island's identifier completion offers it.
        val names = proposals("<% APP_NAME = 'demo' %>\n<%= APP_<caret> %>\n").map { it.name }.toSet()
        assertTrue("APP_NAME" in names)
    }

    fun testNoBackPropagationAboveCaret() {
        // Declaration island is BELOW the caret -> not visible -> not offered.
        val names = proposals("<%= APP_<caret> %>\n<% APP_NAME = 'demo' %>\n").map { it.name }.toSet()
        assertFalse("APP_NAME" in names)
    }

    fun testConstDeclarationBelowCaretIsOffered() {
        val names = proposals("<% const upper = Name %>\n<%= up<caret> %>\n").map { it.name }.toSet()
        assertTrue("upper" in names)
    }

    // ---- spec: graceful degradation (task 2.2) ----

    fun testUnterminatedTagStillOffersContextVariables() {
        // An unterminated `<%` must still offer context-variable completion, not drop it.
        val names = proposals("---\nto: app/x.js\n---\n<%= <caret>\n").map { it.name }.toSet()
        assertTrue(setOf("name", "Name", "names", "Names", "h").all { it in names })
    }

    fun testMissingConfigFallsBackToEngineDefaults() {
        // With an empty context (no config) the `h.` completion serves only the built-in catalog.
        val context = HygenContext(variables = EngineDefaults.names + "h", helpers = emptySet())
        val names = proposals("hello <%= h.<caret> %>\n", context).map { it.name }.toSet()
        assertEquals(setOf("changeCase", "inflection", "capitalize", "path"), names)
        // And identifier completion still offers the engine-name variants.
        val vars = proposals("hello <%= <caret> %>\n", context).map { it.name }.toSet()
        assertEquals(setOf("name", "Name", "names", "Names", "h"), vars)
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
