package com.biorate.hygen.lang.injection

import com.biorate.hygen.lang.HygenLanguage
import com.biorate.hygen.lang.psi.HygenEjsIsland
import com.biorate.hygen.lang.psi.HygenFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ProcessingContext

/**
 * Tests for [HygenEjsReferenceProvider] and [HygenEjsReferenceContributor] (design.md D4 fallback).
 *
 * The provider only ever registers a reference for a JavaScript root reference expression that sits
 * inside an injected fragment from a [HygenEjsIsland]. Materialising injected fragments in a unit
 * test needs the full editor/commit plumbing, so the end-to-end resolution is deliberately left to
 * the sandbox (task 4.2); what is asserted here is the *safety* contract that makes that path
 * harmless: for any element that is not such an injection root, the provider returns an empty array
 * and never throws, so ordinary JavaScript and non-island elements are untouched.
 */
class HygenEjsReferenceProviderTest : BasePlatformTestCase() {

    fun testProviderReturnsEmptyForNonInjectedElement() {
        // A Hygen EJS island is not a JS reference expression: the provider must contribute nothing.
        val file = PsiFileFactory.getInstance(project)
            .createFileFromText("x.ejs.t", HygenLanguage, "---\nto: app/x.js\n---\n<%= h.changeCase.param(name) %>\n") as HygenFile
        val island = PsiTreeUtil.findChildOfType(file, HygenEjsIsland::class.java)!!
        val refs = HygenEjsReferenceProvider().getReferencesByElement(
            island,
            ProcessingContext(),
        )
        assertEquals(0, refs.size)
    }

    fun testProviderReturnsEmptyForBodyLeaf() {
        val file = PsiFileFactory.getInstance(project)
            .createFileFromText("x.ejs.t", HygenLanguage, "---\nto: app/x.js\n---\nconst answer = 42\n") as HygenFile
        val leaf = file.findElementAt(file.text.indexOf("const"))
        val refs = HygenEjsReferenceProvider().getReferencesByElement(leaf!!, ProcessingContext())
        assertEquals(0, refs.size)
    }

    fun testReferenceContributorRegistersWithoutThrowing() {
        // The contributor wires the provider into the JS reference machinery; it must not throw.
        HygenEjsReferenceContributor().registerReferenceProviders(RecordingRegistrar())
    }

    /** A minimal [com.intellij.psi.PsiReferenceRegistrar] that only records registrations. */
    private class RecordingRegistrar : com.intellij.psi.PsiReferenceRegistrar() {
        override fun <T : com.intellij.psi.PsiElement> registerReferenceProvider(
            pattern: com.intellij.patterns.ElementPattern<T>,
            provider: com.intellij.psi.PsiReferenceProvider,
            priority: Double,
        ) = Unit
    }
}
