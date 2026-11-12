package com.biorate.hygen.inspection

import com.biorate.hygen.lang.HygenLanguage
import com.biorate.hygen.lang.psi.HygenFile
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Spec-driven tests for the Level-1 frontmatter inspections (spec.md `hygen-validation`).
 *
 * Each inspection is a [com.intellij.codeInspection.LocalInspectionTool]; the fixtures are built
 * with [PsiFileFactory.createFileFromText] using [HygenLanguage] explicitly, because the platform
 * does not resolve the plugin's dotted `.ejs.t` extension through the test fixture's
 * file-type-by-name lookup (the fixture would otherwise hand back a PLAIN_TEXT file and the
 * Hygen-language inspections would never run).
 *
 * The inspections are driven directly through their `buildVisitor` with a recording
 * [ProblemsHolder], the same deterministic pattern the injector tests use. Error inspections mark
 * their problems with [ProblemHighlightType.GENERIC_ERROR] and warning inspections with
 * [ProblemHighlightType.GENERIC_ERROR_OR_WARNING], so the assertions distinguish the two families.
 * (Severity is configured per-inspection via the `level` attribute in `plugin.xml`.)
 */
class HygenFrontmatterInspectionTest : BasePlatformTestCase() {

    private val unknownMsg = "Unknown frontmatter key"
    private val injectMsg = "mutually exclusive"
    private val anchorMsg = "ignored in add mode"
    private val unterminatedMsg = "Unterminated frontmatter"

    /** Builds a Hygen file and runs all four frontmatter inspections over it. */
    private fun problems(text: String): List<ProblemDescriptor> {
        val file = PsiFileFactory.getInstance(project)
            .createFileFromText("hello.ejs.t", HygenLanguage, text) as HygenFile
        val collected = mutableListOf<ProblemDescriptor>()
        for (tool in listOf(
            HygenUnknownFrontmatterKeyInspection(),
            HygenInjectReplaceConflictingInspection(),
            HygenAnchorWithoutInjectInspection(),
            HygenUnterminatedFrontmatterInspection(),
            HygenSetupValueInspection(),
            HygenFromValueInspection(),
        )) {
            val holder = ProblemsHolder(InspectionManager.getInstance(project), file, false)
            file.accept(tool.buildVisitor(holder, false))
            collected += holder.results
        }
        return collected
    }

    private fun List<ProblemDescriptor>.warningsContaining(text: String): List<ProblemDescriptor> =
        filter { it.highlightType == ProblemHighlightType.GENERIC_ERROR_OR_WARNING && it.descriptionTemplate.contains(text) }

    private fun List<ProblemDescriptor>.errorsContaining(text: String): List<ProblemDescriptor> =
        filter { it.highlightType == ProblemHighlightType.GENERIC_ERROR && it.descriptionTemplate.contains(text) }

    // ---- spec: Fork-specific keys accepted ----
    fun testForkSpecificKeysAreNotUnknown() {
        val infos = problems("---\nto: app/x.js\ncreate_if_missing: true\nreplace: true\n---\ncontent\n")
        assertEquals(0, infos.warningsContaining(unknownMsg).size)
    }

    // ---- spec: Unknown key warns ----
    fun testUnknownKeyWarnsWithName() {
        val infos = problems("---\nto: app/x.js\ntoo: app/x.js\n---\ncontent\n")
        val warns = infos.warningsContaining(unknownMsg)
        assertEquals(1, warns.size)
        assertTrue(warns.single().descriptionTemplate.contains("too"))
        // The warning does not suppress the known-key file: no errors, one warning total.
        assertEquals(0, infos.errorsContaining(unknownMsg).size)
    }

    fun testUnknownKeyDoesNotSuppressOtherInspection() {
        // `too` is unknown and `before` lacks inject -> one warning from each inspection.
        val infos = problems("---\ntoo: app/x.js\nbefore: \"// a\"\n---\ncontent\n")
        assertEquals(1, infos.warningsContaining(unknownMsg).size)
        assertEquals(1, infos.warningsContaining(anchorMsg).size)
    }

    // ---- spec: inject together with replace ----
    fun testInjectWithReplaceConflicts() {
        val infos = problems("---\nto: app/x.js\ninject: true\nreplace: true\n---\ncontent\n")
        assertEquals(2, infos.errorsContaining(injectMsg).size)
    }

    fun testInjectFalseWithReplaceIsNotConflicting() {
        val infos = problems("---\nto: app/x.js\ninject: false\nreplace: true\n---\ncontent\n")
        assertEquals(0, infos.errorsContaining(injectMsg).size)
    }

    // ---- spec: before without inject ----
    fun testBeforeWithoutInjectWarns() {
        val infos = problems("---\nto: app/x.js\nbefore: \"// anchor\"\n---\ncontent\n")
        val warns = infos.warningsContaining(anchorMsg)
        assertEquals(1, warns.size)
        assertTrue(warns.single().descriptionTemplate.contains("before"))
    }

    fun testAnchorWithInjectIsNotWarned() {
        val infos = problems("---\nto: app/x.js\ninject: true\nbefore: \"// anchor\"\n---\ncontent\n")
        assertEquals(0, infos.warningsContaining(anchorMsg).size)
    }

    fun testEveryAnchorOptionWithoutInjectWarns() {
        val infos = problems("---\nto: app/x.js\nbefore: a\nafter: b\nprepend: c\nappend: d\nat_line: 1\n---\ncontent\n")
        assertEquals(5, infos.warningsContaining(anchorMsg).size)
    }

    // ---- spec: broken frontmatter delimiter ----
    fun testUnterminatedFrontmatterErrors() {
        val infos = problems("---\nto: app/x.js\nbody text\n")
        val errs = infos.errorsContaining(unterminatedMsg)
        assertEquals(1, errs.size)
        assertEquals(TextRange(0, "---\nto: app/x.js\nbody text\n".length), errs.single().textRangeInElement)
    }

    fun testClosedFrontmatterIsNotUnterminated() {
        val infos = problems("---\nto: app/x.js\n---\ncontent\n")
        assertEquals(0, infos.errorsContaining(unterminatedMsg).size)
    }

    // ---- negative: a valid template has no frontmatter warnings/errors ----
    fun testValidTemplateHasNoFrontmatterProblems() {
        val infos = problems("---\nto: app/x.js\nsh: echo hi\nlocal: name\n---\ncontent\n")
        assertEquals(0, infos.warningsContaining(unknownMsg).size)
        assertEquals(0, infos.errorsContaining(injectMsg).size)
        assertEquals(0, infos.warningsContaining(anchorMsg).size)
        assertEquals(0, infos.errorsContaining(unterminatedMsg).size)
    }

    // ---- graceful PSI traversal: escaped tags and unterminated EJS never trip the checks ----
    fun testEscapedTagInValueIsNotReported() {
        // `<%%` is a literal escape; it must not surface as an unknown key or a broken block.
        val infos = problems("---\nto: app/<%%= name %>.js\n---\ncontent\n")
        assertEquals(0, infos.warningsContaining(unknownMsg).size)
        assertEquals(0, infos.errorsContaining(unterminatedMsg).size)
    }

    fun testUnterminatedEjsInClosedFrontmatterIsNotReported() {
        // An unterminated `<%` inside a properly closed frontmatter block is not a broken block;
        // traversal over the unterminated EJS island must not crash or produce a false positive.
        val infos = problems("---\nto: app/x.js\ninject: <%= x\n---\ncontent\n")
        assertEquals(0, infos.errorsContaining(unterminatedMsg).size)
        assertEquals(0, infos.warningsContaining(unknownMsg).size)
    }
}
