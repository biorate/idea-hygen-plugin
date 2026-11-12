package com.biorate.hygen.lang.injection

import com.biorate.hygen.catalog.HygenChangeCaseCatalog
import com.biorate.hygen.catalog.HygenInflectionCatalog
import com.biorate.hygen.context.ContextSources
import com.biorate.hygen.context.HygenContext
import com.biorate.hygen.context.HygenTemplateContext
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Tests for [HygenEjsPrelude] (spec: synthetic declarations for the template context).
 *
 * The prelude is the synthetic JavaScript ahead of an injected EJS island: a pure function of a
 * [HygenContext], so it is exercised here directly. The assertions pin the whole contract --
 * the `h` skeleton with every catalog member, the visible context variables, the `.hygen.js`
 * helpers as callable-or-any (design.md D5) -- so no jsdrift in the catalogs can silently shrink
 * what the injected fragment declares.
 */
class HygenEjsPreludeTest : BasePlatformTestCase() {

    private fun contextOf(
        variables: Set<String> = emptySet(),
        helpers: Set<String> = emptySet(),
    ): HygenContext = HygenTemplateContext.merge(
        ContextSources(configLocals = variables, configHelpers = helpers),
    )

    fun testPreludeDeclaresHNamespace() {
        val prelude = HygenEjsPrelude.build(contextOf())
        assertTrue("prelude should open the h object", prelude.contains("const h = {"))
        assertTrue("prelude should declare changeCase", prelude.contains("changeCase: {"))
        assertTrue("prelude should declare inflection", prelude.contains("inflection: {"))
        assertTrue("prelude should declare capitalize", prelude.contains("capitalize: (...args) => ''"))
        assertTrue("prelude should declare path", prelude.contains("path: (...args) => ''"))
        // The h object closes right after the last declared member.
        assertTrue(
            "prelude should close the h object",
            prelude.contains("  path: (...args) => '',\n};\n"),
        )
    }

    fun testPreludeDeclaresAllChangeCaseMembers() {
        val prelude = HygenEjsPrelude.build(contextOf())
        for (member in HygenChangeCaseCatalog.members.keys) {
            assertTrue(
                "changeCase member '$member' missing from prelude",
                prelude.contains("$member: (...args) => ''"),
            )
        }
        // Spot-check the alias and the full name from the scenario.
        assertTrue(prelude.contains("param: (...args) => ''"))
        assertTrue(prelude.contains("camel: (...args) => ''"))
    }

    fun testPreludeDeclaresAllInflectionMembers() {
        val prelude = HygenEjsPrelude.build(contextOf())
        for (member in HygenInflectionCatalog.members.keys) {
            assertTrue(
                "inflection member '$member' missing from prelude",
                prelude.contains("$member: (...args) => ''"),
            )
        }
        // Spot-check the fork extension member.
        assertTrue(prelude.contains("undasherize: (...args) => ''"))
    }

    fun testPreludeDeclaresContextVariables() {
        val prelude = HygenEjsPrelude.build(contextOf(variables = setOf("name", "Name", "names", "Names")))
        for (variable in listOf("name", "Name", "names", "Names")) {
            assertTrue("variable '$variable' not declared", prelude.contains("const $variable = '';"))
        }
    }

    fun testPreludeSkipsHFromVariables() {
        // `h` is already declared by the namespace; a second `const h` would be a redeclaration.
        val prelude = HygenEjsPrelude.build(contextOf(variables = setOf("h")))
        assertEquals(1, countOccurrences(prelude, "const h"))
    }

    fun testPreludeDeclaresConfigHelpersCallableOrAny() {
        // design.md D5: helpers are names only; the shape `(...args) => null` is callable-or-any.
        val prelude = HygenEjsPrelude.build(contextOf(helpers = setOf("root", "stringify")))
        assertTrue("helper 'root' not callable-or-any", prelude.contains("const root = (...args) => null;"))
        assertTrue("helper 'stringify' not callable-or-any", prelude.contains("const stringify = (...args) => null;"))
    }

    fun testPreludeDeclaresEngineDefaultsEvenWithoutContext() {
        // Even an empty (broken) context must still declare the engine defaults + h.
        val prelude = HygenEjsPrelude.build(HygenTemplateContext.merge(ContextSources()))
        assertTrue("name not declared", prelude.contains("const name = '';"))
        assertTrue("Name not declared", prelude.contains("const Name = '';"))
        assertTrue("names not declared", prelude.contains("const names = '';"))
        assertTrue("Names not declared", prelude.contains("const Names = '';"))
    }

    fun testMemberNamesMirrorsPrelude() {
        val context = contextOf(helpers = setOf("root"))
        val members = HygenEjsPrelude.hMemberNames(context)
        assertTrue("changeCase" in members)
        assertTrue("capitalize" in members)
        assertTrue("path" in members)
        assertTrue("root" in members)
    }

    fun testPreludeParsesAsJavaScript() {
        // The prelude must be forward-compatible with the island code it prefixes: prelude + code
        // is a single valid JS statement list.
        val prelude = HygenEjsPrelude.build(contextOf(helpers = setOf("root")))
        val full = prelude + "h.changeCase.param(name);\nroot('dir/x');\n"
        // The prelude is a statement list: every `{`/`(` is balanced, and the number of `const`
        // declarations is exactly `h` + engine-default variables (name variants + the per-run
        // specials `ts`/`_`) + config helpers.
        assertEquals(countOccurrences(prelude, "{"), countOccurrences(prelude, "}"))
        assertEquals(countOccurrences(prelude, "("), countOccurrences(prelude, ")"))
        val expectedConsts = 1 + 4 + 2 + 1
        assertEquals(expectedConsts, countOccurrences(prelude, "const "))
        // Every named member is a callable (`=>`), never a bare name.
        assertTrue(prelude.contains("=>"))
    }

    private fun countOccurrences(text: String, needle: String): Int {
        var count = 0
        var idx = text.indexOf(needle)
        while (idx >= 0) {
            count++
            idx = text.indexOf(needle, idx + needle.length)
        }
        return count
    }
}
