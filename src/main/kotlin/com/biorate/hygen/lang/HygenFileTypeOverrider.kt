package com.biorate.hygen.lang

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.impl.FileTypeOverrider
import com.intellij.openapi.vfs.VirtualFile

/**
 * Recognizes hygen templates that live under a project-local `_templates` directory even
 * when their name does not follow the `.ejs.t` convention. The engine treats every file in
 * an action folder as a template except the hook files listed in [HookFiles].
 *
 * Never calls [VirtualFile.getFileType] here: the file type of a file is resolved through
 * this overrider, so doing that would recurse.
 */
class HygenFileTypeOverrider : FileTypeOverrider {
    override fun getOverriddenFileType(file: VirtualFile): FileType? {
        if (file.isDirectory) return null
        if (file.name.endsWith(".${HygenFileType.EXTENSION}")) return null
        if (HookFiles.isHookFile(file.name)) return null
        if (!HookFiles.isUnderTemplatesDir(file.path)) return null
        return HygenFileType
    }
}
