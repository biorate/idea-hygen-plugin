package com.biorate.hygen.context

import java.io.File

/**
 * Pure filesystem lookup of the `_templates` directory and the `from:` copy source (task 3.1).
 *
 * The engine's `ops/add.ts` resolves a `from:` value against the engine's templates directory —
 * `path.join(args.templates, from)`, where `args.templates` is by default
 * `path.resolve(cwd, '_templates')` (`config-resolver.ts`). An IDE has no CLI cwd, so this plugin
 * proxies it by walking up from the template file to the nearest `_templates` directory; the
 * resolved `from` file is then `_templates/<from>`.
 *
 * These functions use [java.io.File] so they are trivially testable against temp directories and
 * never depend on the IDE's virtual file system.
 */
object HygenTemplatesLocator {
    const val TEMPLATES_DIR_NAME = "_templates"

    /**
     * The nearest `_templates` directory at or above [startDir], walking parent directories up to
     * the filesystem root; null when no such directory exists anywhere above.
     */
    fun findNearestTemplatesDir(startDir: File): File? {
        var dir: File? = startDir.absoluteFile
        while (dir != null) {
            val candidate = File(dir, TEMPLATES_DIR_NAME)
            if (candidate.isDirectory) return candidate
            dir = dir.parentFile
        }
        return null
    }

    /**
     * The path the engine would read for a `from:` value: the file [from] (relative) resolved
     * against the nearest `_templates` above [startDir], or null when no `_templates` is found.
     */
    fun resolveFromTarget(startDir: File, from: String): File? {
        val templatesDir = findNearestTemplatesDir(startDir) ?: return null
        return File(templatesDir, from)
    }
}
