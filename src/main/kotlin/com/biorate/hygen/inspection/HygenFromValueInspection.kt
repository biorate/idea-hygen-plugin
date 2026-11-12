package com.biorate.hygen.inspection

import com.biorate.hygen.context.HygenTemplatesLocator
import com.biorate.hygen.lang.frontmatterPairs
import com.biorate.hygen.lang.psi.HygenFile
import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import java.io.File

/**
 * Warns when a `from:` frontmatter value points at a file that does not exist (spec: from attribute
 * file existence).
 *
 * The fork engine's `ops/add.ts` resolves a `from:` value against the engine's templates directory
 * (`path.join(args.templates, from)`). An IDE has no CLI cwd, so this inspection walks up from the
 * template file to the nearest `_templates` directory and resolves the value against it, mimicking
 * the engine. When no `_templates` directory is found above, the inspection stays silent (no false
 * positives); when the referenced file exists, it is silent too.
 */
class HygenFromValueInspection : HygenFileInspection() {

    override fun check(file: HygenFile, holder: ProblemsHolder) {
        for (pair in file.frontmatterPairs) {
            if (pair.key != "from") continue
            val from = pair.valueText
            // An absent/empty value is not a copy source the engine would read; stay silent.
            if (from.isBlank()) continue

            val templateDir = file.virtualFile?.parent ?: continue
            val target = HygenTemplatesLocator.resolveFromTarget(File(templateDir.path), from) ?: continue
            // No `_templates` above → silent; existing target → silent.
            if (target.exists()) continue

            val anchor = pair.valueElement ?: pair.keyElement ?: continue
            holder.registerProblem(
                anchor,
                "'from:' file not found: ${target.path}",
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            )
        }
    }

    override fun getShortName(): String = "HygenFromValue"
    override fun getDisplayName(): String = "From file not found under _templates"
    override fun getGroupDisplayName(): String = "Hygen"
    override fun getDefaultLevel(): HighlightDisplayLevel = HighlightDisplayLevel.WARNING
}
