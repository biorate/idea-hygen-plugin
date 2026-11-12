package com.biorate.hygen.settings

import org.jetbrains.jps.model.fileTypes.FileNameMatcherFactory

/**
 * Pure normalization/validation/matching of user-configured hygen template filename patterns
 * (design D3).
 *
 * A valid entry has one of two forms:
 *  - **wildcard form** `*.<suffix>`: a single leading `*` covering an arbitrary prefix, followed by
 *    a non-empty dot-separated extension suffix (e.g. `*.t`, `*.ejs.t`, `*.tmpl`).
 *  - **bare extension token**: a plain, non-wildcard extension such as `t` or `ejs.t`. Such an
 *    entry is normalized to its wildcard form by prefixing `*.`. Only a `*`-/`/`-free token that
 *    either contains a `.` (dotted extension) or is exactly the default extension `t` is accepted;
 *    a bare word such as `abc` has neither a wildcard nor a suffix form and is rejected.
 *
 * These rules mirror [com.intellij.openapi.fileTypes.WildcardFileNameMatcher] semantics, where a
 * `*.t` pattern matches the whole file name (any prefix end in `.t`) and is used by the settings
 * page and the dynamic association component to reject non-filename input.
 */
object HygenPatterns {

    /** Default pattern: claim all plain `.t` files. */
    const val DEFAULT = "*.t"

    /** Built-in one-click presets exposed by the settings page. */
    val PRESETS: List<String> = listOf("*.t", "*.ejs.t", "*.tmpl")

    private const val DEFAULT_EXTENSION_TOKEN = "t"

    /**
     * Acceptable characters inside a single extension segment, e.g. `t`, `ejs`, `tmpl_2`.
     */
    private val EXTENSION_SEGMENT = Regex("[A-Za-z0-9_-]+")

    /**
     * Normalizes a raw user entry into a useable file-name pattern, or returns `null` when the
     * entry cannot be interpreted as a valid template pattern.
     *
     * - Surrounding whitespace is trimmed and the case is normalized to lower case.
     * - An already-wildcard entry (`*.t`) passes through unchanged.
     * - A bare extension token (`t`, `ejs.t`) is prefixed to its wildcard form.
     * - Anything invalid (empty, path separators, extra/mid-string wildcards, a bare word) is `null`.
     */
    fun normalize(input: String): String? {
        val token = input.trim().lowercase()
        if (token.isEmpty()) return null
        return if (token.startsWith("*")) {
            if (validateWildcard(token) == null) token else null
        } else {
            if (validateBare(token) == null) "*.$token" else null
        }
    }

    /**
     * Returns a human-readable validation error for [input], or `null` when it is a valid template
     * pattern. This is the single validator the settings page reuses for its error messages; the
     * rules are the same as [normalize] rejects.
     */
    fun validate(input: String): String? {
        if (input.isBlank()) return "Pattern must not be empty"
        if (input.contains('/')) return "Pattern must not contain a path separator"
        val token = input.trim().lowercase()
        return if (token.startsWith("*")) {
            validateWildcard(token)
        } else {
            validateBare(token)
        }
    }

    /**
     * Returns whether [fileName] is claimed by [pattern] using the platform
     * [com.intellij.openapi.fileTypes.WildcardFileNameMatcher], i.e. the exact matcher the IDE
     * installs for a file-type `patterns=` association.
     */
    fun matches(pattern: String, fileName: String): Boolean =
        FileNameMatcherFactory.getInstance().createMatcher(pattern).acceptsCharSequence(fileName)

    private fun validateWildcard(wildcard: String): String? {
        if (!wildcard.startsWith("*.")) {
            return "Pattern must be a wildcard form such as *.t (a single leading * and an extension)"
        }
        val suffix = wildcard.substring(2)
        if (!isValidSuffix(suffix)) {
            return "Pattern must have a non-empty dot-separated extension with no extra wildcards"
        }
        return null
    }

    /** Errors for a bare, non-wildcard extension token, or `null` when valid. */
    private fun validateBare(token: String): String? {
        if (token.contains('*')) {
            return "A wildcard must appear only as a single leading * (e.g. *.t)"
        }
        val isDotted = token.contains('.') && isValidSuffix(token)
        val isDefault = token == DEFAULT_EXTENSION_TOKEN
        return if (isDotted || isDefault) {
            null
        } else {
            "Pattern must have a wildcard/suffix form (e.g. *.t or a dotted extension such as ejs.t)"
        }
    }

    /**
     * A valid suffix is one or more non-empty, wildcard-/slash-free extension segments joined by
     * single dots.
     */
    private fun isValidSuffix(suffix: String): Boolean {
        if (suffix.isEmpty()) return false
        if (suffix.contains('*') || suffix.contains('/')) return false
        return suffix.split('.').all { EXTENSION_SEGMENT.matches(it) }
    }
}
