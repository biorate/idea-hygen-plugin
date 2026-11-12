package com.biorate.hygen.inspection

import com.biorate.hygen.lang.HygenTokenTypes
import com.biorate.hygen.lang.frontmatterPairs
import com.biorate.hygen.lang.psi.HygenEjsIsland
import com.biorate.hygen.lang.psi.HygenFile
import com.biorate.hygen.lang.psi.HygenFrontmatterKeyValue
import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.util.PsiTreeUtil

/**
 * Warns when a `setup:` frontmatter value has no `/` (spec: setup attribute value validation).
 *
 * The fork engine's `ops/setup.ts` requires a setup value to be an `owner/repo` degit source and
 * throws `SetupRepositoryError` when it lacks a `/`, so such a value fails only at generation time.
 * This inspection surfaces it statically. Values containing `/` (a valid repository reference),
 * absent/empty values, and dynamic values that contain an EJS island (e.g. `<%= name %>`, which
 * renders to an `owner/repo` at generation time) are not flagged.
 */
class HygenSetupValueInspection : HygenFileInspection() {

    override fun check(file: HygenFile, holder: ProblemsHolder) {
        for (pair in file.frontmatterPairs) {
            if (pair.key != "setup") continue
            // A value containing an EJS island renders at generation time, so it cannot be judged
            // statically; leave it to the engine (no false positive on `setup: <%= name %>`).
            if (pair.containsEjsIsland()) continue
            val value = pair.valueText
            // Empty value is 'ignored' by the engine (ops/setup.ts notEmpty), so only a non-blank
            // static value without a `/` is a mistake.
            if (value.isBlank() || value.contains('/')) continue
            val anchor = pair.valueElement ?: pair.keyElement ?: continue
            holder.registerProblem(
                anchor,
                "'setup:' must be an 'owner/repo' repository reference (no '/' triggers the engine's SetupRepositoryError at generation time)",
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            )
        }
    }

    override fun getShortName(): String = "HygenSetupValue"
    override fun getDisplayName(): String = "Setup value without owner/repo"
    override fun getGroupDisplayName(): String = "Hygen"
    override fun getDefaultLevel(): HighlightDisplayLevel = HighlightDisplayLevel.WARNING
}

/** True when the pair's value region contains an EJS island (`<% ... %>`) or an unterminated EJS
 *  tag, i.e. a value that is (at least partly) rendered at generation time. */
private fun HygenFrontmatterKeyValue.containsEjsIsland(): Boolean =
    PsiTreeUtil.findChildOfType(this, HygenEjsIsland::class.java) != null ||
        node.findChildByType(HygenTokenTypes.UNTERMINATED_EJS) != null
