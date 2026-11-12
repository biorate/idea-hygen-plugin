package com.biorate.hygen.context

import java.io.File

/**
 * Pure filesystem lookup of a template's config and prompt hooks (task 2.1).
 *
 * The engine's `config-resolver.ts` walks from the template's folder up to the filesystem root,
 * taking the first existing `.hygen.js`. The prompt hook is the first existing file among the
 * engine's hook names inside the template's action folder.
 *
 * These functions use [java.io.File] so they are trivially testable against temp directories and
 * never depend on the IDE's virtual file system.
 */
object HygenConfigLocator {
    const val CONFIG_FILE_NAME = ".hygen.js"

    /** Hook file names in the engine's resolution order (prompt hook reads first-existing). */
    val HOOK_FILE_NAMES: List<String> =
        listOf("prompt.js", "prompt.ts", "index.js", "index.ts", "prompt.cjs")

    /**
     * The nearest `.hygen.js` at or above [startDir], walking parent directories up to the
     * filesystem root; the first existing one wins. Null when no config exists anywhere above.
     */
    fun findNearestConfig(startDir: File): File? {
        var dir: File? = startDir.absoluteFile
        while (dir != null) {
            val candidate = File(dir, CONFIG_FILE_NAME)
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        return null
    }

    /**
     * The first existing prompt hook in [actionDir] per the engine's name order ([HOOK_FILE_NAMES]),
     * or null when the folder has no hook.
     */
    fun findHook(actionDir: File): File? =
        HOOK_FILE_NAMES.asSequence().map { File(actionDir, it) }.firstOrNull { it.isFile }
}
