package com.biorate.hygen.catalog

/**
 * Single source of truth for the known frontmatter keys of the `@biorate/hygen` fork
 * (design.md D6). The unknown-key inspection reads from here, and the completion contributor
 * (task 6.2) reads from the same object, so validation and completion never drift.
 *
 * Structure is deliberately data-first and extensible: the change-case 3.x and inflection 1.x
 * helper member lists live in [HygenChangeCaseCatalog] and [HygenInflectionCatalog], the other half
 * of the design.md D6 single source of truth, so validation and completion never drift.
 */
object HygenFrontmatterCatalog {

    /** Known frontmatter key -> short description, in canonical order. */
    val keys: LinkedHashMap<String, String> = linkedMapOf(
        "to" to "output file path",
        "inject" to "merge the rendered content into an existing file at an anchor",
        "sh" to "shell command to run",
        "before" to "anchor: insert before this marker line",
        "after" to "anchor: insert after this marker line",
        "prepend" to "anchor: insert at the top of the target file",
        "append" to "anchor: insert at the bottom of the target file",
        "at_line" to "anchor: insert at this 1-based line number",
        "skip_if" to "skip when this file path already exists",
        "skip_if_not" to "skip unless this file path exists",
        "create_if_missing" to "create the target file only if it is missing",
        "replace" to "replace the target file's existing content",
        "unless_exists" to "skip when this file path exists",
        "local" to "local template variable (not passed into partials)",
        // ---- extra keys read by the @biorate/hygen fork engine (ops/*, execute.ts) ----
        "setup" to "clone a degit repository into the target path",
        "force" to "overwrite the target file / clone without confirmation",
        "message" to "print a message when the action runs",
        "echo" to "echo the action result to the console",
        "spinner" to "show a spinner while a shell command runs",
        "sh_ignore_exit" to "ignore a non-zero shell exit code",
        "mode" to "degit clone mode for a setup action",
        "verbose" to "verbose output for a setup/shell action",
        "from" to "copy the body from a source file instead of the template body",
        "eof_last" to "preserve/trim the trailing newline around an injected body",
        "mirror" to "opt-in mirroring: copy the file to a path mirrored from the source when `to:` is absent",
    )

    val knownKeys: Set<String> get() = keys.keys

    fun isKnown(key: String): Boolean = key in keys

    fun description(key: String): String? = keys[key]
}
