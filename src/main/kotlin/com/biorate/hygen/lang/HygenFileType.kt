package com.biorate.hygen.lang

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

/**
 * File type of `@biorate/hygen` templates.
 *
 * Per design D1 the `<fileType>` declaration in `plugin.xml` no longer carries a static
 * `patterns` attribute: associations are applied at runtime by [HygenTemplatePatternsLifecycle]
 * from the user-configurable pattern list (default `*.t`, see [com.biorate.hygen.settings.HygenPatterns]),
 * so a settings change takes effect without an IDE restart.
 */
object HygenFileType : LanguageFileType(HygenLanguage) {
    const val EXTENSION = "t"

    override fun getName(): String = "Hygen Template"

    override fun getDescription(): String = "@biorate/hygen template (frontmatter + EJS body)"

    override fun getDefaultExtension(): String = EXTENSION

    override fun getIcon(): Icon = AllIcons.FileTypes.Text
}
