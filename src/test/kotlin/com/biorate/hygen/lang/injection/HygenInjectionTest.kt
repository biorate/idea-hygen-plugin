package com.biorate.hygen.lang.injection

import com.biorate.hygen.lang.HygenLanguage
import com.biorate.hygen.lang.HygenTokenTypes
import com.biorate.hygen.lang.psi.HygenBody
import com.biorate.hygen.lang.psi.HygenFile
import com.intellij.lang.Language
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Tests for [HygenMultiHostInjector] (design.md D3).
 *
 * The injector is driven directly through a recording [MultiHostRegistrar]: this exercises the
 * exact contract the platform injection engine consumes — the host language chosen by
 * [HygenHostDetection] and the text ranges handed to [MultiHostRegistrar.addPlace] — without
 * depending on the editor/commit plumbing the lazy multi-language engine needs to materialize an
 * injected document for an in-memory [HygenFile]. The end-to-end injected-document rendering is
 * left for the sandbox visual check.
 *
 * Fixtures are created with [com.intellij.psi.PsiFileFactory.createFileFromText] so the body PSI
 * (and thus the [HygenTokenTypes.BODY_TEXT] runs the injector registers) is built.
 *
 * Note the assertions use the two-argument [org.junit.Assert.assertEquals] (JUnit 3) form, since
 * [BasePlatformTestCase] extends [junit.framework.TestCase], which would otherwise reinterpret the
 * first string argument as the failure message.
 */
class HygenInjectionTest : BasePlatformTestCase() {

    private fun configure(text: String, name: String = "x.ejs.t"): HygenFile =
        com.intellij.psi.PsiFileFactory.getInstance(project).createFileFromText(name, HygenLanguage, text) as HygenFile

    /** Drives the injector over the body and records host language + injected ranges. */
    private fun inject(file: HygenFile): Record = Record().also { record ->
        val body = PsiTreeUtil.findChildOfType(file, HygenBody::class.java)!!
        HygenMultiHostInjector().getLanguagesToInject(record.registrar, body)
        record.host = body
    }

    fun testInjectsJavaScriptForJsTarget() {
        // spec: `to: app/x.js` -> the plain body text is injected as JavaScript.
        val file = configure("---\nto: app/x.js\n---\nconst answer = 42\n")
        val record = inject(file)
        assertEquals("JavaScript", record.languageId)
        assertEquals("const answer = 42\n", record.places.single().text)
        // The injection host is the HygenBody composite (the only host the engine queries),
        // and the injected range hits the body's plain text.
        assertEquals(HygenTokenTypes.BODY, record.places.single().host!!.node.elementType)
    }

    fun testNoInjectionWhenTargetHasNoExtension() {
        // spec: `to: app/<%= name %>` for `hello.ejs.t` -> no extractable extension anywhere.
        val file = configure("---\nto: app/<%= name %>\n---\nconst answer = 42\n", "hello.ejs.t")
        val record = inject(file)
        assertNull(record.languageId)
        assertEquals(0, record.places.size)
    }

    fun testEjsIslandIsNotPartOfAnyInjectedRange() {
        // The EJS island splits the body into two plain-text runs; the island itself is excluded.
        val file = configure("---\nto: app/x.js\n---\nconst a = <%= name %>;\nconst b = 2;\n")
        val record = inject(file)
        assertEquals("JavaScript", record.languageId)
        assertEquals(listOf("const a = ", ";\nconst b = 2;\n"), record.places.map { it.text })
        assertTrue(record.places.none { it.text.contains("<%") })
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
                    places += Place(host.text.substring(rangeWithinHost.startOffset, rangeWithinHost.endOffset), host)
                    return this
                }

                override fun doneInjecting() = Unit
            }

        data class Place(val text: String, val host: PsiLanguageInjectionHost?)
    }
}
