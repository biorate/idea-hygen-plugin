package com.biorate.hygen.context

import java.io.File

/**
 * Pure filesystem lookup of a template's config and prompt hooks (task 2.1).
 *
 * The engine's `config-resolver.ts` / `config.ts` walks from the template's folder up to the
 * filesystem root; within **each** directory it checks the four config-file names in priority
 * order (`CONFIG_FILE_NAMES`) and takes the first existing one. The nearest directory wins, so a
 * `.hygen.js` shadows a `.hygen.ts` in a nearer directory only when both sit in that nearer
 * directory. The prompt hook is the first existing file among the engine's hook names inside the
 * template's action folder.
 *
 * These functions use [java.io.File] so they are trivially testable against temp directories and
 * never depend on the IDE's virtual file system.
 */
object HygenConfigLocator {
    /** Config-file names in the engine's resolution order (nearest directory wins; first per dir). */
    val CONFIG_FILE_NAMES: List<String> = listOf(".hygen.js", ".hygen.cjs", ".hygen.mjs", ".hygen.ts")

    /** Hook file names in the engine's resolution order (prompt hook reads first-existing). */
    val HOOK_FILE_NAMES: List<String> =
        listOf("prompt.js", "prompt.ts", "index.js", "index.ts", "prompt.cjs")

    /**
     * The nearest config at or above [startDir], walking parent directories up to the filesystem
     * root. Within each directory the engine's name order is consulted ([CONFIG_FILE_NAMES]); the
     * first existing candidate wins. Null when no config exists anywhere above.
     */
    fun findNearestConfig(startDir: File): File? {
        var dir: File? = startDir.absoluteFile
        while (dir != null) {
            for (name in CONFIG_FILE_NAMES) {
                val candidate = File(dir, name)
                if (candidate.isFile) return candidate
            }
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
