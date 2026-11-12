package com.biorate.hygen.lang.injection

import com.intellij.lang.Language
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiLanguageInjectionHost
import kotlin.Pair

/**
 * Reflective, gracefully-degrading adapter over the platform's
 * `com.intellij.lang.injection.MultiHostRegistrarPlaceholderHelper` (design.md D4, spike-verified).
 *
 * That helper lives in `app.jar` / `app-client.jar` (the platform product), NOT on the plugin's
 * compile classpath (which is the platform-core `lib` jars only), so it is reached reflectively
 * here. It wraps a [MultiHostRegistrar] and injects placeholder TEXT at given ranges — the
 * mechanism used to prepend the synthetic [HygenEjsPrelude] to an injected island fragment while
 * leaving the host's visible text untouched.
 *
 * [create] returns null when the helper class is absent on the runtime classpath (or on any
 * reflective failure), so the caller degrades to plain injection — the same graceful degradation
 * the platform modules rely on (design.md D4 fallback). [inject] then drives the helper: start the
 * injection for [language], hand the host's [codeRange] (relative to the host) the [prelude] text,
 * and finish.
 */
internal class HygenEjsPlaceholderInjector private constructor(
    private val startInjecting: (Language) -> Any?,
    private val addHostPlaces: (PsiLanguageInjectionHost, List<Pair<TextRange, String>>) -> Any?,
    private val doneInjecting: () -> Any?,
) {


    /**
     * Registers the JS [language] injection into [host] with [prelude] prepended at [codeRange].
     * The helper splits the injection so the prelude text is inserted and the host's own code text
     * at [codeRange] follows it. Any reflective hiccup is swallowed: the injection is skipped
     * rather than risking a partially-registered fragment.
     */
    fun inject(language: Language, host: PsiLanguageInjectionHost, codeRange: TextRange, prelude: String) {
        try {
            startInjecting(language)
            addHostPlaces(host, listOf(Pair(codeRange, prelude)))
            doneInjecting()
        } catch (_: Throwable) {
            // Never let a reflective failure propagate out of an injector (graceful degradation).
        }
    }

    companion object {
        /** The placeholder-helper's fully qualified platform class name. */
        private const val HELPER_CLASS = "com.intellij.lang.injection.MultiHostRegistrarPlaceholderHelper"

        /**
         * Wraps [registrar] in a placeholder helper, or null when the helper class is unavailable
         * (not on the runtime classpath) or any step of the reflective wiring fails.
         */
        fun create(registrar: MultiHostRegistrar): HygenEjsPlaceholderInjector? = try {
            val cls = Class.forName(HELPER_CLASS)
            val helper = cls.getConstructor(MultiHostRegistrar::class.java).newInstance(registrar)
            HygenEjsPlaceholderInjector(
                startInjecting = { language -> method(cls, "startInjecting", Language::class.java).invoke(helper, language) },
                addHostPlaces = { host, places ->
                    method(cls, "addHostPlaces", PsiLanguageInjectionHost::class.java, List::class.java)
                        .invoke(helper, host, places)
                },
                doneInjecting = { method(cls, "doneInjecting").invoke(helper) },
            )
        } catch (_: Throwable) {
            null
        }

        private fun method(cls: Class<*>, name: String, vararg params: Class<*>): java.lang.reflect.Method =
            cls.getMethod(name, *params)
    }
}
