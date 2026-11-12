package com.biorate.hygen.inspection

import com.biorate.hygen.lang.hasUnterminatedFrontmatter
import com.biorate.hygen.lang.psi.HygenFile
import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange

/**
 * Errors when a frontmatter block is opened by a `---` line but never closed by a matching `---`
 * line (spec: broken frontmatter delimiter). The lexer treats such a run as body text, so the
 * check is text-level on the file; it highlights the whole unclosed block up to end of file.
 */
class HygenUnterminatedFrontmatterInspection : HygenFileInspection() {

    override fun check(file: HygenFile, holder: ProblemsHolder) {
        if (file.hasUnterminatedFrontmatter) {
            holder.registerProblem(
                file,
                "Unterminated frontmatter: '---' block is not closed by a matching '---' line",
                ProblemHighlightType.GENERIC_ERROR,
                TextRange(0, file.textLength),
            )
        }
    }

    override fun getShortName(): String = "HygenUnterminatedFrontmatter"
    override fun getDisplayName(): String = "Unterminated frontmatter block"
    override fun getGroupDisplayName(): String = "Hygen"
    override fun getDefaultLevel(): HighlightDisplayLevel = HighlightDisplayLevel.ERROR
}
