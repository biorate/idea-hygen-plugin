package com.biorate.hygen.inspection

import com.biorate.hygen.catalog.HygenFrontmatterCatalog
import com.biorate.hygen.lang.frontmatterPairs
import com.biorate.hygen.lang.psi.HygenFile
import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder

/**
 * Warns on frontmatter keys outside the `@biorate/hygen` fork catalog (spec: frontmatter key
 * validation). Reported as WARNING because arbitrary keys may legitimately serve as template
 * variables, so a hard error would be too aggressive.
 */
class HygenUnknownFrontmatterKeyInspection : HygenFileInspection() {

    override fun check(file: HygenFile, holder: ProblemsHolder) {
        for (pair in file.frontmatterPairs) {
            val key = pair.key ?: continue
            if (!HygenFrontmatterCatalog.isKnown(key)) {
                val anchor = pair.keyElement ?: continue
                holder.registerProblem(
                    anchor,
                    "Unknown frontmatter key '${key}'",
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                )
            }
        }
    }

    override fun getShortName(): String = "HygenUnknownFrontmatterKey"
    override fun getDisplayName(): String = "Unknown frontmatter key"
    override fun getGroupDisplayName(): String = "Hygen"
    override fun getDefaultLevel(): HighlightDisplayLevel = HighlightDisplayLevel.WARNING
}
