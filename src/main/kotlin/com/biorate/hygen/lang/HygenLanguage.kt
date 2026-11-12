package com.biorate.hygen.lang

import com.intellij.lang.Language

/**
 * Language of `@biorate/hygen` template files: YAML frontmatter followed by an
 * EJS-rendered body (see design.md D2).
 */
object HygenLanguage : Language("HygenTemplate") {
    private fun readResolve(): Any = HygenLanguage

    override fun getDisplayName(): String = "Hygen Template"

    override fun isCaseSensitive(): Boolean = true
}
