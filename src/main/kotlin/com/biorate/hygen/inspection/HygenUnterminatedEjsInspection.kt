package com.biorate.hygen.inspection

import com.biorate.hygen.lang.ejsUnterminatedTags
import com.biorate.hygen.lang.psi.HygenFile
import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder

/**
 * Errors on an unterminated EJS tag (spec: EJS tag integrity) — an opening `<%` (optionally with
 * the `=`, `-` or `#` marker) that never gets a matching `%>`. The lexer leaves each such run as
 * a [com.biorate.hygen.lang.HygenTokenTypes.UNTERMINATED_EJS] node, so the check walks those nodes
 * and marks each one.
 *
 * No false positives: the `<%%` literal-escape sequence is tokenized as an escape, and a
 * properly closed `<% ... %>` becomes a full EJS island — neither produces an
 * [com.biorate.hygen.lang.HygenTokenTypes.UNTERMINATED_EJS] node.
 */
class HygenUnterminatedEjsInspection : HygenFileInspection() {

    override fun check(file: HygenFile, holder: ProblemsHolder) {
        for (tag in file.ejsUnterminatedTags) {
            holder.registerProblem(
                tag,
                "Unterminated EJS tag: missing closing '%>'",
                ProblemHighlightType.GENERIC_ERROR,
            )
        }
    }

    override fun getShortName(): String = "HygenUnterminatedEjs"
    override fun getDisplayName(): String = "Unterminated EJS tag"
    override fun getGroupDisplayName(): String = "Hygen"
    override fun getDefaultLevel(): HighlightDisplayLevel = HighlightDisplayLevel.ERROR
}
