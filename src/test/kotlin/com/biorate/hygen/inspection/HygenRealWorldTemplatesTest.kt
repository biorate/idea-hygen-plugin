package com.biorate.hygen.inspection

import com.biorate.hygen.lang.HygenLanguage
import com.biorate.hygen.lang.psi.HygenFile
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assume.assumeTrue
import java.io.File

/**
 * Integration check (task 5.3): the full Level-1 inspection suite must report ZERO problems —
 * no warnings and no errors — on the real-world hygen templates shipped by the `@biorate/hygen`
 * package whose sources live in the sibling `/www/biorate/core` monorepo.
 *
 * The package's genuine templates are read straight off disk:
 *
 *  - `src/templates/**/*.ejs.t` — the templates the package itself ships to bootstrap a new
 *    generator / repository (e.g. `setup`, `force`, `message` frontmatter keys);
 *  - `tests/fixtures/**/*.ejs.t` — real template usage samples the package tests against
 *    (the `inject-error` subtree is excluded because it is *deliberately* malformed to exercise
 *    the engine's error path).
 *
 * Each file is parsed into a [HygenFile] with [PsiFileFactory.createFileFromText] (the same
 * deterministic harness the spec-driven inspection tests use) and run through every file-level
 * inspection. The hook-file EJS inspection is intentionally not applied: the loaded files are
 * `.ejs.t` templates, not the `prompt.js`/`index.js` hook files it targets.
 *
 * The check SKIPS gracefully (via JUnit `assumeTrue`) when the `@biorate/hygen` sources are not
 * present on this machine, so it cannot fail a build that runs without the sibling repo.
 *
 * Override the package location with `-Dhygen.biorate.package.root=/path/to/@biorate/hygen`.
 */
class HygenRealWorldTemplatesTest : BasePlatformTestCase() {

    /** The `@biorate/hygen` package source dir, or null when not present on disk. */
    private fun packageRoot(): File? {
        System.getProperty("hygen.biorate.package.root")?.let {
            val root = File(it)
            if (root.isDirectory) return root
        }
        return CANDIDATE_ROOTS.asSequence().map { File(it) }.firstOrNull { it.isDirectory }
    }

    fun testRealWorldTemplatesHaveZeroProblems() {
        val root = packageRoot()
        assumeTrue(
            "@biorate/hygen package sources not found on disk; skipping real-world template check",
            root != null,
        )
        val dir = root!!

        val templates = collectRealTemplates(dir)
        // Guard the intended fixture: if the sources exist but no templates were collected that is
        // a real failure, not a skip.
        assertTrue("no real-world .ejs.t templates collected under $dir", templates.isNotEmpty())

        val problemsByFile = runFileInspections(templates)
        val failures = problemsByFile.filter { it.value.isNotEmpty() }
        assertEquals(
            "Real-world templates produced inspection problems.${describe(failures)}",
            0,
            failures.size,
        )
    }

    /** All real `.ejs.t` templates: shipped ones + valid test fixtures (minus the broken ones). */
    private fun collectRealTemplates(root: File): List<File> {
        val collected = sortedSetOf<File>()
        for (base in listOf(File(root, "src/templates"), File(root, "tests/fixtures"))) {
            if (!base.isDirectory) continue
            base.walkTopDown()
                .filter { it.isFile && it.name.endsWith(".ejs.t") }
                .filterNot { it.path.contains("inject-error") }
                .forEach { collected += it }
        }
        return collected.toList().sortedBy { it.path }
    }

    /** Runs every file-level (non-hook) inspection over each document; returns path -> problems. */
    private fun runFileInspections(files: List<File>): Map<String, List<ProblemDescriptor>> =
        files.associate { file ->
            val hygen = PsiFileFactory.getInstance(project)
                .createFileFromText(file.name, HygenLanguage, file.readText()) as HygenFile
            val collected = mutableListOf<ProblemDescriptor>()
            for (tool in FILE_INSPECTIONS) {
                val holder = ProblemsHolder(InspectionManager.getInstance(project), hygen, false)
                hygen.accept(tool.buildVisitor(holder, false))
                collected += holder.results
            }
            file.path to collected
        }

    private fun describe(failures: Map<String, List<ProblemDescriptor>>): String = failures.entries
        .joinToString(prefix = "\n", separator = "\n") { (path, list) ->
            path + "\n  " + list.joinToString("\n  ") {
                "${it.highlightType.name} @${it.textRangeInElement}: ${it.descriptionTemplate}"
            }
        }

    companion object {
        /** Absolute paths where the sibling `/www/biorate/core` monorepo's hygen package may live. */
        private val CANDIDATE_ROOTS = listOf(
            "/www/biorate/core/packages/@biorate/hygen",
        )

        private val FILE_INSPECTIONS: List<LocalInspectionTool> = listOf(
            HygenUnknownFrontmatterKeyInspection(),
            HygenInjectReplaceConflictingInspection(),
            HygenAnchorWithoutInjectInspection(),
            HygenUnterminatedFrontmatterInspection(),
            HygenUnterminatedEjsInspection(),
        )
    }
}
