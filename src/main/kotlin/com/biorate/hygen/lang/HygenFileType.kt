package com.biorate.hygen.lang

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

/** File type of `@biorate/hygen` templates (`.ejs.t`). */
object HygenFileType : LanguageFileType(HygenLanguage) {
    const val EXTENSION = "ejs.t"

    /**
     * File-name wildcard the platform resolves templates by. IntelliJ's `FileTypeManager`
     * matches an extension registered in the `<fileType extensions="...">` attribute by the
     * segment after the last dot, so a compound extension like `ejs.t` never matches a file
     * named `...prettierrc.ejs.t` (it falls through to whoever owns `*.t`). The `patterns`
     * attribute instead registers a [com.intellij.openapi.fileTypes.WildcardFileNameMatcher]
     * that matches the whole file name, so `.ejs.t` files are claimed here while plain
     * `.t` files are left to the Perl file type.
     */
    const val FILE_NAME_PATTERN = "*.$EXTENSION"

    override fun getName(): String = "Hygen Template"

    override fun getDescription(): String = "@biorate/hygen template (frontmatter + EJS body)"

    override fun getDefaultExtension(): String = EXTENSION

    override fun getIcon(): Icon = AllIcons.FileTypes.Text
}
