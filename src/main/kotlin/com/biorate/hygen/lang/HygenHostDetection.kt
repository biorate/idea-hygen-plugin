package com.biorate.hygen.lang

import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.LanguageFileType

/**
 * Host-language detection for a hygen template body (design.md D4).
 *
 * The host language is the language the rendered body is written in. Detection is a pure
 * function of the raw `to:` frontmatter value and the template file name:
 *
 * 1. the extension of the last path segment of `to:`. The value is never rendered, so EJS
 *    islands are irrelevant here as long as the *last* segment carries a literal extension
 *    (`app/<%= section %>/<%= name %>.ts` yields `ts`);
 * 2. otherwise the extension of the file name with the conventional `.t` / `.ejs` suffixes
 *    removed (`hello.js.ejs.t` -> `hello.js` -> `js`);
 * 3. otherwise `null`, meaning plain text (EJS tags are still highlighted by the lexer).
 *
 * Extensions are mapped to languages through the platform file-type registry, so the mapping
 * follows whatever languages the IDE actually has installed. An extension with no registered
 * language degrades to `null` rather than guessing.
 */
object HygenHostDetection {

    /**
     * A literal extension at the very end of a name, e.g. `js` in `report.js`. The character
     * before the dot must not itself be a dot, so a dotfile (`.gitignore`) has no extension.
     */
    private val EXTENSION = Regex("""[^.]\.([A-Za-z0-9]+)$""")

    fun detectHost(toValue: String?, fileName: String): Language? {
        val extension = extensionFromTarget(toValue) ?: extensionFromFileName(fileName)
        return extension?.let(::languageForExtension)
    }

    private fun extensionFromTarget(toValue: String?): String? {
        if (toValue.isNullOrBlank()) return null
        val lastSegment = toValue.replace('\\', '/').substringAfterLast('/')
        return EXTENSION.find(lastSegment)?.groupValues?.get(1)
    }

    private fun extensionFromFileName(fileName: String): String? {
        val base = fileName.removeSuffix(".t").removeSuffix(".ejs")
        return EXTENSION.find(base)?.groupValues?.get(1)
    }

    private fun languageForExtension(extension: String): Language? {
        val fileType = FileTypeManager.getInstance().getFileTypeByExtension(extension)
        return (fileType as? LanguageFileType)?.language
    }
}
