package com.biorate.hygen.inspection

import com.biorate.hygen.lang.frontmatterPairs
import com.biorate.hygen.lang.psi.HygenFile
import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder

/**
 * Errors when both `inject: true` and `replace: true` are present (spec: mutually exclusive
 * inject options). The fork treats them as exclusive write modes, so combining them is a
 * configuration mistake. The error is reported on both conflicting keys.
 */
class HygenInjectReplaceConflictingInspection : HygenFileInspection() {

    override fun check(file: HygenFile, holder: ProblemsHolder) {
        val injectPair = file.frontmatterPairs.firstOrNull { it.key == "inject" && it.isTrue } ?: return
        val replacePair = file.frontmatterPairs.firstOrNull { it.key == "replace" && it.isTrue } ?: return

        for (pair in listOf(injectPair, replacePair)) {
            val anchor = pair.keyElement ?: continue
            holder.registerProblem(
                anchor,
                "'inject: true' and 'replace: true' are mutually exclusive write modes",
                ProblemHighlightType.GENERIC_ERROR,
            )
        }
    }

    override fun getShortName(): String = "HygenInjectReplaceConflicting"
    override fun getDisplayName(): String = "Mutually exclusive inject and replace"
    override fun getGroupDisplayName(): String = "Hygen"
    override fun getDefaultLevel(): HighlightDisplayLevel = HighlightDisplayLevel.ERROR
}
