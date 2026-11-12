package com.biorate.hygen.inspection

import com.biorate.hygen.lang.HookFiles
import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile

/**
 * Warns when EJS tag syntax appears in a hygen hook file (spec: EJS syntax in hook files).
 *
 * Hook files (`prompt.js`, `index.js`, `prompt.ts`, `index.ts`) inside an action folder of a
 * `_templates` tree are executed as JavaScript and are *not* rendered by the engine, so a
 * `<% ... %>` tag pasted into one is never interpolated. The check is a plain-text scan over the
 * file, so it keeps working on the JS/TS file the overrider deliberately leaves as a hook (per
 * design.md: the overrider excludes hook files from template parsing).
 *
 * The `language` attribute is intentionally omitted from the plugin.xml registration so the
 * inspection also sees the JS/TS hook files, which are not hygen templates.
 */
class HygenEjsInHookFileInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitFile(file: PsiFile) {
                val path = file.virtualFile?.path ?: return
                if (!HookFiles.isHookFileAtPath(path)) return
                if (!EJS_TAG.containsMatchIn(file.text)) return
                holder.registerProblem(
                    file,
                    "EJS tags are not processed in hook files; ${file.name} is run as JavaScript and is never rendered",
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    file.textRange,
                )
            }
        }

    override fun getShortName(): String = "HygenEjsInHookFile"
    override fun getDisplayName(): String = "EJS tags in hook file"
    override fun getGroupDisplayName(): String = "Hygen"
    override fun getDefaultLevel(): HighlightDisplayLevel = HighlightDisplayLevel.WARNING

    private companion object {
        /** An opening `<%` that is not the `<%%` literal-escape sequence. */
        val EJS_TAG = Regex("<%(?!%)")
    }
}
