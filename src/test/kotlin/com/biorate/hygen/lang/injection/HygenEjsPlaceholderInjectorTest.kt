package com.biorate.hygen.lang.injection

import com.biorate.hygen.lang.HygenLanguage
import com.biorate.hygen.lang.psi.HygenEjsIsland
import com.biorate.hygen.lang.psi.HygenFile
import com.intellij.lang.Language
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.psi.PsiFileFactory

/**
 * Tests for [HygenEjsPlaceholderInjector], the reflective adapter over the platform's
 * `MultiHostRegistrarPlaceholderHelper` (design.md D4 primary mechanism).
 *
 * The helper lives in `app.jar`, which is on the IDE runtime classpath of the test JVM, so
 * [HygenEjsPlaceholderInjector.create] must succeed here (the reflective guard is exercised) and
 * [inject] must reach the wrapped [MultiHostRegistrar]'s `addPlace`/`doneInjecting` contract. This
 * proves the placeholder mechanism is reachable at runtime and degrades by not throwing; whether a
 * *placeholder-prefixed prelude* proves out in a real editor is the sandbox pass (task 4.2), so the
 * injector keeps the plain-injection fallback and the reference contributor carries resolution.
 */
class HygenEjsPlaceholderInjectorTest : BasePlatformTestCase() {

    fun testCreateWrapsRegistrarWhenClassPresent() {
        // The placeholder helper class exists in the test IDE, so the guard must not degrade.
        val injector = HygenEjsPlaceholderInjector.create(RecordingRegistrar())
        assertNotNull("placeholder helper should be reflectively reachable in the IDE", injector)
    }

    fun testInjectReachesRegistrarAndCompletes() {
        val file = PsiFileFactory.getInstance(project)
            .createFileFromText("x.ejs.t", HygenLanguage, "---\nto: app/x.js\n---\n<%= name %>\n") as HygenFile
        val island = PsiTreeUtil.findChildOfType(file, HygenEjsIsland::class.java)!!
        val range = islandCodeRange(island)!!
        val language: Language = Language.findLanguageByID("JavaScript") ?: return

        val registrar = RecordingRegistrar()
        val injector = HygenEjsPlaceholderInjector.create(registrar)!!
        injector.inject(language, island, range, "const h = {};\n")

        // The wrapped registrar must have been told `doneInjecting` (i.e. the injection completed)
        // and the language was started — the placeholders reach the engine contract even if the
        // exact fragment layout is editor-dependent.
        assertTrue("injector should have started the injection", registrar.started)
        assertTrue("injector should have finished the injection", registrar.done)
    }

    /** A recording registrar capturing the lifecycle calls the helper makes on the wrapped one. */
    private class RecordingRegistrar : MultiHostRegistrar {
        var started = false
        var done = false

        override fun startInjecting(language: Language): MultiHostRegistrar {
            started = true
            return this
        }

        override fun addPlace(
            prefix: String?,
            suffix: String?,
            host: PsiLanguageInjectionHost,
            rangeWithinHost: TextRange,
        ): MultiHostRegistrar {
            started = true
            return this
        }

        override fun doneInjecting() {
            done = true
        }
    }
}
