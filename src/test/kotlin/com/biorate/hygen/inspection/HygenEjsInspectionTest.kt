package com.biorate.hygen.inspection

import com.biorate.hygen.lang.HygenLanguage
import com.biorate.hygen.lang.psi.HygenFile
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Spec-driven tests for the EJS-level-1 inspections (spec.md `hygen-validation`):
 *
 * - [HygenUnterminatedEjsInspection] runs over a [HygenFile] built with
 *   [PsiFileFactory.createFileFromText] (the same deterministic harness as the frontmatter
 *   tests), and asserts error-vs-clean behaviour around unterminated tags, `<%%` escapes and
 *   properly closed tags.
 * - [HygenEjsInHookFileInspection] runs over the *hook* files, which the file-type overrider
 *   deliberately leaves as ordinary JS/TS (not hygen templates), so those fixtures are created
 *   with [com.intellij.testFramework.fixtures.CodeInsightTestFixture.addFileToProject] to give
 *   them a real `_templates` VirtualFile path to test the template-directory scoping.
 */
class HygenEjsInspectionTest : BasePlatformTestCase() {

    private val unterminatedMsg = "Unterminated EJS tag"
    private val hookMsg = "EJS tags are not processed in hook files"

    /** Builds a Hygen file and runs the unterminated-EJS inspection over it. */
    private fun unterminatedProblems(text: String): List<ProblemDescriptor> {
        val file = PsiFileFactory.getInstance(project)
            .createFileFromText("hello.ejs.t", HygenLanguage, text) as HygenFile
        val holder = ProblemsHolder(InspectionManager.getInstance(project), file, false)
        file.accept(HygenUnterminatedEjsInspection().buildVisitor(holder, false))
        return holder.results
    }

    /** Creates a real file at [path] and runs the hook-EJS inspection over it. */
    private fun hookProblems(path: String, text: String): List<ProblemDescriptor> {
        val file = myFixture.addFileToProject(path, text)
        val holder = ProblemsHolder(InspectionManager.getInstance(project), file, false)
        file.accept(HygenEjsInHookFileInspection().buildVisitor(holder, false))
        return holder.results
    }

    private fun List<ProblemDescriptor>.errorsContaining(text: String): List<ProblemDescriptor> =
        filter { it.highlightType == ProblemHighlightType.GENERIC_ERROR && it.descriptionTemplate.contains(text) }

    private fun List<ProblemDescriptor>.warningsContaining(text: String): List<ProblemDescriptor> =
        filter { it.highlightType == ProblemHighlightType.GENERIC_ERROR_OR_WARNING && it.descriptionTemplate.contains(text) }

    // ---- spec: Unterminated tag ----
    fun testUnterminatedTagErrors() {
        val infos = unterminatedProblems("---\nto: app/x.js\n---\n<%= name\n")
        assertEquals(1, infos.errorsContaining(unterminatedMsg).size)
    }

    fun testUnterminatedTagInFrontmatterValueErrors() {
        // An unterminated `<%` inside a properly closed frontmatter block is still a broken tag,
        // not a broken *block*; the EJS check must catch it (the frontmatter check must not).
        val infos = unterminatedProblems("---\nto: app/x.js\ninject: <%= x\n---\ncontent\n")
        assertEquals(1, infos.errorsContaining(unterminatedMsg).size)
    }

    fun testClosedTagFollowedByUnterminatedErrorsOnce() {
        // Only the unterminated run is reported; the preceding properly closed tag is clean.
        val infos = unterminatedProblems("---\nto: app/x.js\n---\n<%= a %>\n<%= b\n")
        assertEquals(1, infos.errorsContaining(unterminatedMsg).size)
    }

    // ---- spec: Escaped literal ----
    fun testEscapedLiteralIsNotError() {
        // `<%%` is the literal escape; it must not surface as an unterminated tag.
        val infos = unterminatedProblems("---\nto: app/x.js\n---\n<%%= name %>\n")
        assertEquals(0, infos.errorsContaining(unterminatedMsg).size)
    }

    // ---- negative: a properly closed tag is clean ----
    fun testClosedTagIsNotError() {
        val infos = unterminatedProblems("---\nto: app/x.js\n---\n<%= name %>\n")
        assertEquals(0, infos.errorsContaining(unterminatedMsg).size)
    }

    // ---- spec: Template pasted into prompt.js ----
    fun testEjsInPromptJsWarns() {
        val infos = hookProblems("_templates/mygen/new/prompt.js", "const x = <%= name %>;\n")
        assertEquals(1, infos.warningsContaining(hookMsg).size)
    }

    fun testEjsInIndexTsWarns() {
        val infos = hookProblems("_templates/mygen/new/index.ts", "<%= name %>\n")
        assertEquals(1, infos.warningsContaining(hookMsg).size)
    }

    // ---- negative: hook file without EJS is clean ----
    fun testHookWithoutEjsDoesNotWarn() {
        val infos = hookProblems("_templates/mygen/new/prompt.js", "module.exports = {};\n")
        assertEquals(0, infos.warningsContaining(hookMsg).size)
    }

    // ---- negative: EJS outside a _templates tree is not a hook warning ----
    fun testEjsOutsideTemplatesDoesNotWarn() {
        val infos = hookProblems("src/prompt.js", "<%= name %>\n")
        assertEquals(0, infos.warningsContaining(hookMsg).size)
    }

    // ---- negative: a non-hook file inside _templates is not a hook warning ----
    fun testNonHookFileInTemplatesDoesNotWarn() {
        val infos = hookProblems("_templates/mygen/new/component.js", "<%= name %>\n")
        assertEquals(0, infos.warningsContaining(hookMsg).size)
    }

    // ---- negative: escaped tag is not EJS ----
    fun testEscapedTagInHookFileDoesNotWarn() {
        val infos = hookProblems("_templates/mygen/new/prompt.js", "<%%= name %>\n")
        assertEquals(0, infos.warningsContaining(hookMsg).size)
    }
}
