package com.biorate.hygen.lang

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

/** File type of `@biorate/hygen` templates (`.ejs.t`). */
object HygenFileType : LanguageFileType(HygenLanguage) {
    const val EXTENSION = "ejs.t"

    override fun getName(): String = "Hygen Template"

    override fun getDescription(): String = "@biorate/hygen template (frontmatter + EJS body)"

    override fun getDefaultExtension(): String = EXTENSION

    override fun getIcon(): Icon = AllIcons.FileTypes.Text
}
