package com.biorate.hygen.inspection

import com.biorate.hygen.lang.frontmatterPairs
import com.biorate.hygen.lang.psi.HygenFile
import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder

/**
 * Warns when an anchor option (`before`, `after`, `prepend`, `append`, `at_line`) is present
 * but ignored — i.e. the action is in plain add mode (neither `inject: true` nor `replace: true`).
 * Anchors have meaning for the inject operation, and the `before`/`after` anchors are also used
 * by the replace operation (the fork engine's `REPLACE_ANCHORS`); only in add mode are they
 * ignored entirely.
 */
class HygenAnchorWithoutInjectInspection : HygenFileInspection() {

    override fun check(file: HygenFile, holder: ProblemsHolder) {
        val hasInjectTrue = file.frontmatterPairs.any { it.key == "inject" && it.isTrue }
        val hasReplaceTrue = file.frontmatterPairs.any { it.key == "replace" && it.isTrue }

        for (pair in file.frontmatterPairs) {
            if (pair.key !in ANCHOR_KEYS) continue
            // The anchor is meaningful when the action is in inject mode, or — for the
            // `before`/`after` replace anchors — in replace mode. It is only ignored in
            // plain add mode (neither inject nor replace).
            val effective = hasInjectTrue || (hasReplaceTrue && pair.key in REPLACE_ANCHOR_KEYS)
            if (!effective) {
                val anchor = pair.keyElement ?: continue
                holder.registerProblem(
                    anchor,
                    "Anchor '${pair.key}' is ignored in add mode; it only takes effect with 'inject: true' (or 'replace: true' for before/after)",
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                )
            }
        }
    }

    override fun getShortName(): String = "HygenAnchorWithoutInject"
    override fun getDisplayName(): String = "Anchor option without inject mode"
    override fun getGroupDisplayName(): String = "Hygen"
    override fun getDefaultLevel(): HighlightDisplayLevel = HighlightDisplayLevel.WARNING

    private companion object {
        val ANCHOR_KEYS: Set<String> = setOf("before", "after", "prepend", "append", "at_line")

        /** Anchors the fork engine's replace mode honours (ops/injector.ts `REPLACE_ANCHORS`). */
        private val REPLACE_ANCHOR_KEYS: Set<String> = setOf("before", "after")
    }
}
