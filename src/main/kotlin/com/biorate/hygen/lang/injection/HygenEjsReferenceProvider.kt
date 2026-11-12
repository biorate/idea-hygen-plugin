package com.biorate.hygen.lang.injection

import com.biorate.hygen.context.HygenTemplateContext
import com.biorate.hygen.lang.psi.HygenFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.util.ProcessingContext

/**
 * The injected-fragment reference provider for EJS islands (design.md D4 fallback, spec: synthetic
 * declarations for the template context).
 *
 * When `<%= h.changeCase.param(name) %>` is injected as JavaScript, the JS analyzer sees a global
 * `h` (and `name`) it knows nothing about and would flag them unresolved. The primary design
 * mechanism (D4) injects the synthetic [HygenEjsPrelude] ahead of the island code via the platform
 * placeholder helper; that path needs the real IDE to validate and is behind a reflective guard.
 * This provider is the GUARANTEED path (Angular/Vue-proven pattern): it hands the analyzer a
 * resolving reference for the ROOT identifier of any chain that names a template-context identifier
 * (the `h` namespace, a context variable, a `.hygen.js` helper), so nothing false-positive errors.
 * Because the resolved root carries no JS type, the member accesses below it (`h.changeCase.param`)
 * degrade to `any` — callable-or-any, design.md D5 — so wrong arities never error either.
 *
 * It is strictly additive and gated: it only ever registers a reference when the element is a JS
 * reference expression at the ROOT of a chain, the name is a known template-context identifier
 * (see [HygenEjsContextualNames]), and the element sits inside an injected fragment whose host is a
 * [HygenFile] (see [HygenEjsFileInjector] — since `combined-js-injection` the host is the whole
 * file, not the individual island). For every other element it returns an empty array, so normal JS
 * resolution is untouched.
 *
 * The host lookup goes through the platform's `InjectedLanguageUtil.findInjectionHost`, which is in
 * `app.jar` and therefore reached reflectively (like the placeholder helper) — a miss simply means
 * the element is not a hygen EJS injection and the provider contributes nothing.
 */
class HygenEjsReferenceProvider : com.intellij.psi.PsiReferenceProvider() {

    override fun getReferencesByElement(
        element: PsiElement,
        context: ProcessingContext,
    ): Array<PsiReference> {
        // The costliest and most selective gate is first: only elements inside an injected fragment
        // that originated from a HygenFile are ever considered. For every ordinary JS element (and
        // every other injected language) this resolves to null and we return immediately, so the
        // provider is a no-op outside hygen EJS injections.
        val file = fileHost(element) ?: return EMPTY

        // Then only JS reference expressions at the ROOT of a chain (no qualifier).
        val rootName = referencedName(element) ?: return EMPTY
        if (qualifier(element) != null) return EMPTY

        // The visible context at the file start: engine defaults + config locals/helpers + hook
        // variables. In-template declarations (`const x` in an island) are intentionally excluded —
        // since `combined-js-injection` all islands share one JS document, the JS analyzer natively
        // sees a preceding declaration, so the provider only has to cover the identifiers it can
        // never infer (the `h` namespace and the config/hook names).
        val visible = HygenTemplateContext.visibleAt(file, file.textRange.startOffset)

        if (!HygenEjsContextualNames.resolveRoot(visible, rootName, emptyList())) return EMPTY

        return arrayOf(HygenContextualReference(element, rootName, file))
    }

    private companion object {
        val EMPTY = PsiReference.EMPTY_ARRAY

        private const val JS_REFERENCE_EXPRESSION = "JSReferenceExpression"
        private const val INJECTED_LANGUAGE_UTIL = "com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil"

        /** The lazily-resolved `InjectedLanguageUtil.findInjectionHost(PsiElement)` method. */
        private val findInjectionHost: java.lang.reflect.Method? by lazy {
            try {
                Class.forName(INJECTED_LANGUAGE_UTIL)
                    .getMethod("findInjectionHost", PsiElement::class.java)
            } catch (_: Throwable) {
                null
            }
        }

        /** The referenced name when [element] is a JS reference expression; null otherwise. */
        private fun referencedName(element: PsiElement): String? {
            if (element.language.id != "JavaScript") return null
            if (!simpleNameInHierarchy(element, JS_REFERENCE_EXPRESSION)) return null
            return element.call("getReferencedName") as? String
        }

        /** The qualifier expression of a JS reference expression, or null when it has none. */
        private fun qualifier(element: PsiElement): PsiElement? =
            element.call("getQualifier") as? PsiElement

        /**
         * The [HygenFile] host of [element], or null when it is not a hygen EJS injection.
         *
         * Since `combined-js-injection` the injection host is the whole [HygenFile] (not the
         * individual [HygenEjsIsland]), so the formerly-island-gated check now gates on the file.
         */
        private fun fileHost(element: PsiElement): HygenFile? {
            val method = findInjectionHost ?: return null
            val host = try {
                method.invoke(null, element) as? PsiElement
            } catch (_: Throwable) {
                null
            }
            return host as? HygenFile
        }

        /** True when [element]'s class hierarchy carries an interface/class named like [typeName]. */
        private fun simpleNameInHierarchy(element: PsiElement, typeName: String): Boolean {
            val seen = HashSet<Class<*>>()
            val queue = ArrayDeque<Class<*>>()
            element.javaClass.let { queue.add(it) }
            while (queue.isNotEmpty()) {
                val cls = queue.removeFirst()
                if (!seen.add(cls)) continue
                if (cls.simpleName.contains(typeName)) return true
                cls.superclass?.let { queue.add(it) }
                cls.interfaces.forEach { queue.add(it) }
            }
            return false
        }

        /** Invokes a no-arg public method reflectively; null on any failure. */
        private fun PsiElement.call(name: String): Any? = try {
            javaClass.getMethod(name).invoke(this)
        } catch (_: Throwable) {
            null
        }
    }
}

/**
 * A reference from a JS identifier inside an injected EJS island to its [target] (the island) —
 * the resolution that keeps a template-context identifier from being flagged unknown. Because it is
 * [isSoft], it never participates in rename/multiply-declared checks; it exists purely so the
 * analyzer has a resolving reference on the element (design.md D4 fallback).
 */
private class HygenContextualReference(
    private val element: PsiElement,
    private val name: String,
    private val target: PsiElement,
) : PsiReference {
    override fun getElement(): PsiElement = element
    override fun getRangeInElement(): com.intellij.openapi.util.TextRange =
        com.intellij.openapi.util.TextRange(0, name.length)
    override fun resolve(): PsiElement? = target
    override fun getCanonicalText(): String = name
    override fun handleElementRename(newElementName: String): PsiElement = element
    override fun bindToElement(element: PsiElement): PsiElement = element
    override fun isReferenceTo(element: PsiElement): Boolean = resolve() == element
    override fun isSoft(): Boolean = true
}
