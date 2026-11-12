package com.biorate.hygen.lang

/**
 * Files inside a generator action folder that are NOT templates: the engine skips them
 * (`ignores` in `@biorate/hygen` `src/render.ts`), because they are enquirer prompt hooks
 * and plain JS/TS modules executed by the tooling.
 */
object HookFiles {
    val NAMES: Set<String> = setOf("prompt.js", "index.js", "prompt.ts", "index.ts")

    fun isHookFile(fileName: String): Boolean = fileName in NAMES

    /** True when a VFS-style path (forward slashes) lives under a `_templates` directory. */
    fun isUnderTemplatesDir(path: String): Boolean =
        path.contains("/_templates/") || path.startsWith("_templates/")

    /** True when [path] names a hook file that sits inside a `_templates` directory. */
    fun isHookFileAtPath(path: String): Boolean =
        isUnderTemplatesDir(path) && isHookFile(path.substringAfterLast('/'))
}
