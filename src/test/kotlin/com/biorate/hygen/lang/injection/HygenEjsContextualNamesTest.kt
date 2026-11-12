package com.biorate.hygen.lang.injection

import com.biorate.hygen.context.HygenContext
import com.biorate.hygen.context.HygenTemplateContext
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Tests for [HygenEjsContextualNames] (speac: synthetic declarations for the template context) —
 * the pure decision logic shared by the reference contributor. It answers "does this JS root
 * identifier name a template-context identifier that must resolve?", mirroring the identifiers the
 * [HygenEjsPrelude] declares.
 */
class HygenEjsContextualNamesTest : BasePlatformTestCase() {

    private fun contextOf(variables: Set<String>, helpers: Set<String>): HygenContext =
        HygenTemplateContext.merge(
            com.biorate.hygen.context.ContextSources(configLocals = variables, configHelpers = helpers),
        )

    private fun vars(vararg names: String) = names.toSet()

    fun testHNamespaceAlwaysResolves() {
        val context = contextOf(emptySet(), emptySet())
        assertTrue(HygenEjsContextualNames.resolveRoot(context, "h", emptyList()))
    }

    fun testEngineNameVariantsResolve() {
        val context = contextOf(emptySet(), emptySet())
        // Engine defaults: name/Name/names/Names are always visible (context).
        assertTrue(HygenEjsContextualNames.resolveRoot(context, "name", emptyList()))
        assertTrue(HygenEjsContextualNames.resolveRoot(context, "Name", emptyList()))
        assertTrue(HygenEjsContextualNames.resolveRoot(context, "names", emptyList()))
        assertTrue(HygenEjsContextualNames.resolveRoot(context, "Names", emptyList()))
    }

    fun testLocalsAndHookVariablesResolve() {
        val context = contextOf(vars("scope", "owner"), emptySet())
        assertTrue(HygenEjsContextualNames.resolveRoot(context, "scope", emptyList()))
        assertTrue(HygenEjsContextualNames.resolveRoot(context, "owner", emptyList()))
    }

    fun testInTemplateDeclarationResolves() {
        // A `const upper` declared in an earlier island is a visible variable.
        val context = contextOf(vars("upper"), emptySet())
        assertTrue(HygenEjsContextualNames.resolveRoot(context, "upper", emptyList()))
    }

    fun testConfigHelperResolvesBare() {
        // design.md: `.hygen.js` helper `root` is callable at the top level as well as via h.root.
        val context = contextOf(emptySet(), setOf("root"))
        assertTrue(HygenEjsContextualNames.resolveRoot(context, "root", emptyList()))
    }

    fun testUnknownBareIdentifierDoesNotResolve() {
        val context = contextOf(emptySet(), emptySet())
        // A truly unknown identifier stays with the JS analyzer's own (unresolved) reference.
        assertFalse(HygenEjsContextualNames.resolveRoot(context, "someRandomThing", emptyList()))
    }

    fun testHMemberAccessResolves() {
        val context = contextOf(emptySet(), setOf("root"))
        // `h.changeCase` / `h.path` / `h.root` are members of the h namespace.
        assertTrue(HygenEjsContextualNames.resolveRoot(context, "changeCase", listOf("h")))
        assertTrue(HygenEjsContextualNames.resolveRoot(context, "path", listOf("h")))
        assertTrue(HygenEjsContextualNames.resolveRoot(context, "root", listOf("h")))
        assertTrue(HygenEjsContextualNames.resolveRoot(context, "capitalize", listOf("h")))
    }

    fun testHUnknownMemberDoesNotResolve() {
        val context = contextOf(emptySet(), setOf("root"))
        assertFalse(HygenEjsContextualNames.resolveRoot(context, "bogusMember", listOf("h")))
    }

    fun testGroupMembersAreKnown() {
        assertTrue(HygenEjsContextualNames.isGroupMember("changeCase", "param"))
        assertTrue(HygenEjsContextualNames.isGroupMember("changeCase", "pascalCase"))
        assertTrue(HygenEjsContextualNames.isGroupMember("inflection", "pluralize"))
        assertTrue(HygenEjsContextualNames.isGroupMember("inflection", "undasherize"))
        assertFalse(HygenEjsContextualNames.isGroupMember("changeCase", "pluralize"))
        assertFalse(HygenEjsContextualNames.isGroupMember("path", "join"))
    }
}
