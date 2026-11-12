package com.biorate.hygen.inspection

import com.biorate.hygen.lang.psi.HygenFile
import com.biorate.hygen.lang.psi.HygenFrontmatterKeyValue
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile

/**
 * Base for the hygen Level-1 frontmatter inspections. Every check is a standard
 * [LocalInspectionTool] (design.md D5) so users can configure/suppress each one independently.
 *
 * The checks operate on the whole [HygenFile] (e.g. the `inject`+`replace` conflict needs to see
 * the whole frontmatter block), so the visitor dispatches at the file node.
 */
abstract class HygenFileInspection : LocalInspectionTool() {

    final override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitFile(file: PsiFile) {
                if (file is HygenFile) check(file, holder)
            }
        }

    abstract fun check(file: HygenFile, holder: ProblemsHolder)
}

/** Whether this pair's value is the boolean literal `true` (frontmatter booleans are lowercase). */
val HygenFrontmatterKeyValue.isTrue: Boolean
    get() = valueText == "true"
