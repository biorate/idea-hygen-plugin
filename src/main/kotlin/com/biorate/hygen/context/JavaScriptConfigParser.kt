package com.biorate.hygen.context

import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory

/**
 * Reads `.hygen.js` config and action-folder prompt hooks through the platform's JavaScript PSI,
 * WITHOUT executing any code (design.md D1 / D1a, task 2.1).
 *
 * What it extracts:
 * - `localsDefaults` keys of `module.exports = { ... }` -> available template variables;
 * - property names of the `helpers` object literal (methods and getters alike) -> members of `h`;
 * - string `name:` property values from question object literals -> prompt-hook variables;
 * - statically visible keys of object literals returned by / passed to `params(...)`.
 *
 * Only statically extractable names are taken: a dynamic `name: someVar` is skipped silently, and
 * an identifier-referenced helper value (e.g. `_: lodash`) still contributes its property NAME.
 *
 * The JavaScript PSI lives in a bundled plugin that is not on the compile classpath (and may be
 * absent at runtime). To keep the platform dependency minimal and degrade gracefully when the JS
 * plugin is missing, the JS element types are accessed REFLECTIVELY: the parser first looks up the
 * JavaScript language by id, creates a JS [PsiFile], and then walks it with generic PSI calls plus
 * reflective interface method invocations. If the language lookup or any step fails, every function
 * returns an empty set and no error is reported.
 */
object JavaScriptConfigParser {

    private const val JS_LANGUAGE_ID = "JavaScript"

    /** Keys of the exported `localsDefaults` object literal; empty when absent/unparsable. */
    fun localsFromConfig(project: Project, text: String): Set<String> {
        val exported = exportedObjectLiteral(project, text) ?: return emptySet()
        val localsDefaults = exported.findProperty("localsDefaults")?.value ?: return emptySet()
        return localsDefaults.asObjectLiteral()?.propertyNames() ?: emptySet()
    }

    /** Property names of the exported `helpers` object literal; empty when absent/unparsable. */
    fun helpersFromConfig(project: Project, text: String): Set<String> {
        val exported = exportedObjectLiteral(project, text) ?: return emptySet()
        val helpers = exported.findProperty("helpers")?.value ?: return emptySet()
        return helpers.asObjectLiteral()?.propertyNames() ?: emptySet()
    }

    /**
     * Statically visible prompt-hook variables: string `name:` values from question object literals
     * plus keys of object literals returned by / passed to `params(...)`. Dynamic names are skipped.
     */
    fun hookVariables(project: Project, text: String): Set<String> {
        val file = jsFile(project, text) ?: return emptySet()
        return collectQuestionNames(file) + collectParamsKeys(file)
    }

    // ---- config / exported object literal ----

    /** The `module.exports = { ... }` object literal, or null when absent / not parseable. */
    private fun exportedObjectLiteral(project: Project, text: String): JS_OBJECT_LITERAL? {
        val file = jsFile(project, text) ?: return null
        for (el in allElements(file)) {
            if (!el.isJsType("JSAssignmentExpression")) continue
            val lhs = el.call("getLOperand") as? PsiElement ?: continue
            val rhs = el.call("getROperand") as? PsiElement ?: continue
            if (isModuleExportsLhs(lhs)) return rhs.asObjectLiteral()
        }
        return null
    }

    /** True when [lhs] is a `module.exports` reference (possibly wrapped in a JSDefinitionExpression). */
    private fun isModuleExportsLhs(lhs: PsiElement): Boolean {
        for (el in allElements(lhs)) {
            if (!el.isJsType("JSReferenceExpression")) continue
            if (el.call("getReferencedName") != "exports") continue
            val qualifier = el.call("getQualifier") as? PsiElement ?: continue
            if (qualifier.isJsType("JSReferenceExpression") &&
                qualifier.call("getReferencedName") == "module"
            ) {
                return true
            }
        }
        return false
    }

    // ---- prompt hooks ----

    /** String `name:` values of every object literal in the hook file (questions included). */
    private fun collectQuestionNames(file: PsiElement): Set<String> {
        val names = linkedSetOf<String>()
        for (el in allElements(file)) {
            if (!el.isJsType("JSObjectLiteralExpression")) continue
            val nameProp = JS_OBJECT_LITERAL(el).findProperty("name") ?: continue
            val value = nameProp.value ?: continue
            if (!value.isJsType("JSLiteralExpression")) continue
            val literal = JS_LITERAL(value)
            if (literal.isStringLiteral) literal.stringValue?.let { names += it }
        }
        return names
    }

    /** Keys of object literals returned by / passed to `params(...)`. */
    private fun collectParamsKeys(file: PsiElement): Set<String> {
        val keys = linkedSetOf<String>()
        for (el in allElements(file)) {
            if (el.isJsType("JSObjectLiteralExpression") && isParamsRelated(el)) {
                keys += JS_OBJECT_LITERAL(el).propertyNames()
            }
        }
        return keys
    }

    /** True when [obj] is the value of a `params` property, a `params()` argument, or a `params` body. */
    private fun isParamsRelated(obj: PsiElement): Boolean {
        val parent = obj.parent ?: return false

        // `{ params: { locale, server } }` — value of a property named `params`.
        if (parent.isNamedJsProperty("params")) return true

        // `params({ locale, server })` — direct argument of a `params(...)` call.
        if (parent.isJsType("JSArgumentList")) {
            val call = parent.parent ?: return false
            val method = call.call("getMethodExpression") as? PsiElement ?: return false
            if (method.isJsType("JSReferenceExpression") && method.call("getReferencedName") == "params") {
                return true
            }
        }

        // `function params(...) { return { ... } }` / `const params = () => ({...})` — the object
        // literal lives inside (or is the value of) a function whose name resolves to `params`.
        var fn = obj.parent
        while (fn != null && !fn.isJsType("JSFunction")) fn = fn.parent
        if (fn != null) {
            if (fn.call("getName") == "params") return true
            var prop = fn.parent
            while (prop != null && !prop.isNamedJsProperty("params")) prop = prop.parent
            if (prop != null) return true
        }
        return false
    }

    // ---- JS PSI helpers (reflective) ----

    private fun jsFile(project: Project, text: String): PsiElement? {
        if (text.isBlank()) return null
        if (Language.findLanguageByID(JS_LANGUAGE_ID) == null) return null
        return try {
            val fileType = FileTypeManager.getInstance().getFileTypeByFileName("hygen-source.js")
            PsiFileFactory.getInstance(project)
                .createFileFromText("hygen-source.js", fileType, text)
                .let { it as? PsiFile }
        } catch (_: Throwable) {
            null
        }
    }

    private fun allElements(root: PsiElement): List<PsiElement> {
        val out = ArrayList<PsiElement>()
        fun walk(el: PsiElement) {
            out += el
            var child = el.firstChild
            while (child != null) {
                walk(child)
                child = child.nextSibling
            }
        }
        walk(root)
        return out
    }

    /** True when [this] (or a supertype/interface) carries the JS interface [typeName]. */
    private fun PsiElement.isJsType(typeName: String): Boolean {
        val seen = HashSet<Class<*>>()
        val queue = ArrayDeque<Class<*>>()
        javaClass.let { queue.add(it) }
        while (queue.isNotEmpty()) {
            val cls = queue.removeFirst()
            if (!seen.add(cls)) continue
            if (cls.simpleName == typeName || cls.name.substringAfterLast('.') == typeName) return true
            cls.superclass?.let { queue.add(it) }
            cls.interfaces.forEach { queue.add(it) }
        }
        return false
    }

    /** Wraps [this] as a JS object-literal witness when it is one; null otherwise. */
    private fun PsiElement.asObjectLiteral(): JS_OBJECT_LITERAL? =
        if (isJsType("JSObjectLiteralExpression")) JS_OBJECT_LITERAL(this) else null

    /** True when [this] is a JS property named [propertyName]. */
    private fun PsiElement.isNamedJsProperty(propertyName: String): Boolean =
        isJsType("JSProperty") && call("getName") == propertyName

    /** Invokes a no-arg public method reflectively; null on any failure (missing JS API). */
    private fun PsiElement.call(name: String): Any? = try {
        javaClass.getMethod(name).invoke(this)
    } catch (_: Throwable) {
        null
    }

    /** A JS object-literal PSI element, typed by an interface-name witness. */
    private class JS_OBJECT_LITERAL(private val el: PsiElement) {
        init {
            check(el.isJsType("JSObjectLiteralExpression"))
        }

        fun findProperty(key: String): JS_PROPERTY? {
            val prop = try {
                el.javaClass.getMethod("findProperty", String::class.java).invoke(el, key)
            } catch (_: Throwable) {
                null
            } as? PsiElement ?: return null
            return JS_PROPERTY(prop)
        }

        fun propertyNames(): Set<String> {
            val props = el.call("getProperties") as? Array<*> ?: return emptySet()
            val names = linkedSetOf<String>()
            for (p in props) {
                if (p !is PsiElement) continue
                val name = p.call("getName") as? String ?: continue
                if (name.isNotEmpty()) names += name
            }
            return names
        }
    }

    /** A JS property PSI element with a reflective [value] accessor. */
    private class JS_PROPERTY(private val el: PsiElement) {
        init {
            check(el.isJsType("JSProperty"))
        }

        val value: PsiElement?
            get() = el.call("getValue") as? PsiElement
    }

    /** A JS literal PSI element backed by reflective string accessors. */
    private class JS_LITERAL(private val el: PsiElement) {
        init {
            check(el.isJsType("JSLiteralExpression"))
        }

        val isStringLiteral: Boolean
            get() = (el.call("isStringLiteral") as? Boolean) == true

        val stringValue: String?
            get() = el.call("getStringValue") as? String
    }
}
