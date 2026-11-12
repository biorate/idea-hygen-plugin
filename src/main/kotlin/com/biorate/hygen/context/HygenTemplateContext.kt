package com.biorate.hygen.context

import com.biorate.hygen.lang.HygenTokenTypes
import com.biorate.hygen.lang.psi.HygenEjsIsland
import com.biorate.hygen.lang.psi.HygenFile
import com.intellij.psi.util.PsiTreeUtil
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * The raw, pre-merge sources of a [HygenContext]. Each set is independently empty when its source
 * is missing, and the later entries shadow earlier ones of the same name during [HygenTemplateContext.merge].
 */
data class ContextSources(
    val configLocals: Set<String> = emptySet(),
    val configHelpers: Set<String> = emptySet(),
    val hookVariables: Set<String> = emptySet(),
    val templateDeclarations: Set<String> = emptySet(),
)

/**
 * Resolves the identifiers visible to a hygen template (task 2.1, design.md D2).
 *
 * [visibleAt] computes the context at an offset by merging, in order: engine defaults -> nearest
 * `.hygen.js` locals -> prompt-hook variables -> in-template declarations. Later sources shadow
 * earlier ones by name; duplicate names collapse. Config/hook parsing is cached per file +
 * modification stamp (D2), so unrelated files are not re-parsed on every keystroke.
 */
object HygenTemplateContext {

    // ---- public API ----

    /** The merged context visible at [offset] in [file]. */
    fun visibleAt(file: HygenFile, offset: Int): HygenContext =
        merge(
            ContextSources(
                configLocals = configLocals(file),
                configHelpers = configHelpers(file),
                hookVariables = hookVariables(file),
                templateDeclarations = inTemplateDeclarations(file, offset),
            ),
        )

    /** Merges [sources] over the engine defaults; later sources shadow earlier ones by name. */
    fun merge(sources: ContextSources): HygenContext = HygenContext(
        variables = EngineDefaults.context.variables +
            sources.configLocals +
            sources.hookVariables +
            sources.templateDeclarations,
        helpers = EngineDefaults.context.helpers + sources.configHelpers,
    )

    /**
     * Variables declared in EJS islands at or before [offset] (EJS renders sequentially): islands
     * whose end offset is <= [offset]. Declarations are `const/let/var X = ...` and bare `X = ...`.
     */
    fun inTemplateDeclarations(file: HygenFile, offset: Int): Set<String> {
        val declared = linkedSetOf<String>()
        for (island in PsiTreeUtil.collectElementsOfType(file, HygenEjsIsland::class.java)) {
            if (island.textRange.endOffset > offset) continue
            val content = islandContentLeaf(island) ?: continue
            declared += declaredNames(content)
        }
        return declared
    }

    // ---- disk-backed source resolution (with caching) ----

    private fun configLocals(file: HygenFile): Set<String> = configCacheEntry(file).locals

    private fun configHelpers(file: HygenFile): Set<String> = configCacheEntry(file).helpers

    private fun hookVariables(file: HygenFile): Set<String> {
        val dir = actionDir(file) ?: return emptySet()
        val hook = HygenConfigLocator.findHook(dir) ?: return emptySet()
        val path = hook.absolutePath
        val stamp = hook.lastModified()
        hookCache[path]?.takeIf { it.stamp == stamp }?.let { return it.variables }
        val text = hook.readTextSafe() ?: return emptySet()
        val variables = JavaScriptConfigParser.hookVariables(file.project, text)
        hookCache[path] = CachedHook(stamp, variables)
        return variables
    }

    /** Resolves and caches the nearest `.hygen.js` locals + helpers for [file]. */
    private fun configCacheEntry(file: HygenFile): CachedConfig {
        val dir = actionDir(file)
        val config = dir?.let { HygenConfigLocator.findNearestConfig(it) } ?: return CachedConfig.EMPTY
        val path = config.absolutePath
        val stamp = config.lastModified()
        configCache[path]?.takeIf { it.stamp == stamp }?.let { return it }
        val text = config.readTextSafe()
        val locals = text?.let { JavaScriptConfigParser.localsFromConfig(file.project, it) } ?: emptySet()
        val helpers = text?.let { JavaScriptConfigParser.helpersFromConfig(file.project, it) } ?: emptySet()
        return CachedConfig(stamp, locals, helpers).also { configCache[path] = it }
    }

    /** The template's action folder (its parent directory), or null for virtual/pathless files. */
    private fun actionDir(file: HygenFile): File? {
        val parent = file.virtualFile?.parent ?: return null
        return File(parent.path)
    }

    // ---- in-template declaration scan ----

    /** The `const/let/var` and bare-assignment names in one island's script content. */
    private fun declaredNames(content: String): Set<String> {
        val names = linkedSetOf<String>()
        for (m in VAR_DECLARATION.findAll(content)) names += m.groupValues[1]
        for (m in BARE_ASSIGNMENT.findAll(content)) names += m.groupValues[1]
        return names
    }

    /** The EJS scriptlet/expression leaf inside [island], or null for comments / empty islands. */
    private fun islandContentLeaf(island: HygenEjsIsland): String? {
        var child = island.firstChild
        while (child != null) {
            val type = child.node?.elementType
            if (type == HygenTokenTypes.EJS_SCRIPTLET || type == HygenTokenTypes.EJS_EXPRESSION) {
                return child.text
            }
            child = child.nextSibling
        }
        return null
    }

    private fun File.readTextSafe(): String? = runCatching { readText() }.getOrNull()

    private data class CachedConfig(
        val stamp: Long,
        val locals: Set<String>,
        val helpers: Set<String>,
    ) {
        companion object {
            val EMPTY = CachedConfig(-1L, emptySet(), emptySet())
        }
    }

    private data class CachedHook(val stamp: Long, val variables: Set<String>)

    private val configCache = ConcurrentHashMap<String, CachedConfig>()
    private val hookCache = ConcurrentHashMap<String, CachedHook>()

    private val VAR_DECLARATION = Regex("""\b(?:const|let|var)\s+([A-Za-z_$][A-Za-z0-9_$]*)""")
    private val BARE_ASSIGNMENT = Regex("""(?<![\w.$])([A-Za-z_$][A-Za-z0-9_$]*)\s*=(?![=>])""")
}
