package com.biorate.hygen.inspection

import com.biorate.hygen.lang.HygenLanguage
import com.biorate.hygen.lang.psi.HygenFile
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

/**
 * Spec-driven tests for the fork's `setup:`/`from:` value inspections (spec.md `hygen-validation`).
 *
 * The `setup:` check is purely textual over the frontmatter PSI, so its fixtures are built with
 * [PsiFileFactory.createFileFromText] (the same harness as [HygenFrontmatterInspectionTest]). The
 * `from:` check resolves against the nearest `_templates` on disk, so its fixtures are real files
 * loaded through the VFS (the same pattern as [com.biorate.hygen.context.HygenTemplateContextTest]
 * cache test) — otherwise `virtualFile.parent` would be null and the inspection would (correctly)
 * stay silent.
 *
 * Both inspections mark warnings with [ProblemHighlightType.GENERIC_ERROR_OR_WARNING].
 */
class HygenForkFrontmatterValueInspectionTest : BasePlatformTestCase() {

    private val setupMsg = "owner/repo"
    private val fromMsg = "file not found"

    // ---- helpers ----

    private fun runSetup(text: String): List<ProblemDescriptor> {
        val file = PsiFileFactory.getInstance(project)
            .createFileFromText("hello.ejs.t", HygenLanguage, text) as HygenFile
        return run(HygenSetupValueInspection(), file)
    }

    private fun run(tool: HygenFileInspection, file: HygenFile): List<ProblemDescriptor> {
        val holder = ProblemsHolder(InspectionManager.getInstance(project), file, false)
        file.accept(tool.buildVisitor(holder, false))
        return holder.results
    }

    private fun List<ProblemDescriptor>.warningsContaining(text: String): List<ProblemDescriptor> =
        filter { it.highlightType == ProblemHighlightType.GENERIC_ERROR_OR_WARNING && it.descriptionTemplate.contains(text) }

    // ---- spec: setup attribute value validation ----

    fun testSetupWithoutSlashWarns() {
        // `setup: myrepo` (no '/') — reported with the owner/repo shape in the message.
        val infos = runSetup("---\nto: app/x.js\nsetup: myrepo\n---\ncontent\n")
        val warns = infos.warningsContaining(setupMsg)
        assertEquals(1, warns.size)
        assertTrue(warns.single().descriptionTemplate.contains("SetupRepositoryError"))
    }

    fun testValidSetupWithSlashIsNotFlagged() {
        // `setup: biorate/core-templates` (has '/') — no warning.
        val infos = runSetup("---\nto: app/x.js\nsetup: biorate/core-templates\n---\ncontent\n")
        assertEquals(0, infos.warningsContaining(setupMsg).size)
    }

    fun testAbsentSetupIsNotFlagged() {
        val infos = runSetup("---\nto: app/x.js\n---\ncontent\n")
        assertEquals(0, infos.warningsContaining(setupMsg).size)
    }

    fun testEmptySetupIsNotFlagged() {
        // The engine's ops/setup.ts ignores an empty value (`notEmpty`), so it is not flagged.
        val infos = runSetup("---\nto: app/x.js\nsetup:\n---\ncontent\n")
        assertEquals(0, infos.warningsContaining(setupMsg).size)
    }

    fun testDynamicSetupValueWithEjsIslandIsNotFlagged() {
        // `setup: <%= name %>` renders to an owner/repo at generation time, so it is not a static
        // literal and must not be flagged (false positive on the real new-repo.ejs.t template).
        val infos = runSetup("---\nto: app/x.js\nsetup: <%= name %>\n---\ncontent\n")
        assertEquals(0, infos.warningsContaining(setupMsg).size)
    }

    fun testMixedStaticValueWithEjsIslandIsNotFlagged() {
        // A value mixing static text with an EJS island is partly dynamic, so it is not flagged.
        val infos = runSetup("---\nto: app/x.js\nsetup: <%= owner %>/repo\n---\ncontent\n")
        assertEquals(0, infos.warningsContaining(setupMsg).size)
    }

    // ---- spec: from attribute file existence ----

    private fun makeTemplate(withFrom: String): Pair<HygenFile, File> {
        val root = FileUtil.createTempDirectory("hygen-from-test", "")
        val templates = File(root, "_templates")
        val gen = File(templates, "gen").apply { mkdirs() }
        val action = File(gen, "action").apply { mkdirs() }
        val template = File(action, "hello.ejs.t").apply {
            writeText("---\nto: app/x.js\n$withFrom\n---\nbody\n")
        }
        val vf = LocalFileSystem.getInstance().refreshAndFindFileByPath(template.absolutePath)!!
        val file = PsiManager.getInstance(project).findFile(vf) as HygenFile
        return file to root
    }

    fun testFromPointsToMissingFileWarnsWithResolvedPath() {
        val (file, root) = makeTemplate("from: snippets/header.txt")
        val warns = run(HygenFromValueInspection(), file).warningsContaining(fromMsg)
        assertEquals(1, warns.size)
        // The message shows the resolved path `_templates/snippets/header.txt`.
        val expected = File(File(root, "_templates"), "snippets/header.txt").path
        assertTrue(warns.single().descriptionTemplate.contains(expected))
        FileUtil.delete(root)
    }

    fun testFromWithExistingFileIsNotFlagged() {
        val root = FileUtil.createTempDirectory("hygen-from-test", "")
        val templates = File(root, "_templates")
        File(templates, "gen/action").apply { mkdirs() }
        File(templates, "snippets").apply { mkdirs() }
        File(templates, "snippets/header.txt").writeText("existing body")
        val template = File(templates, "gen/action/hello.ejs.t").apply {
            writeText("---\nto: app/x.js\nfrom: snippets/header.txt\n---\nbody\n")
        }
        val vf = LocalFileSystem.getInstance().refreshAndFindFileByPath(template.absolutePath)!!
        val file = PsiManager.getInstance(project).findFile(vf) as HygenFile
        assertEquals(0, run(HygenFromValueInspection(), file).warningsContaining(fromMsg).size)
        FileUtil.delete(root)
    }

    fun testNoTemplatesDirectoryFoundStaysSilent() {
        // No `_templates` anywhere above the file — the inspection must report nothing, whatever
        // the `from:` value (no false positives).
        val root = FileUtil.createTempDirectory("hygen-from-test", "")
        val action = File(root, "gen/action").apply { mkdirs() }
        val template = File(action, "hello.ejs.t").apply {
            writeText("---\nto: app/x.js\nfrom: whatever/not/here.txt\n---\nbody\n")
        }
        val vf = LocalFileSystem.getInstance().refreshAndFindFileByPath(template.absolutePath)!!
        val file = PsiManager.getInstance(project).findFile(vf) as HygenFile
        assertEquals(0, run(HygenFromValueInspection(), file).size)
        FileUtil.delete(root)
    }

    fun testNestedFromPathResolvedUnderTemplates() {
        // A nested `from:` path resolves relative to the `_templates` root and is shown when
        // missing, proving the walk-up finds the templates dir even from a nested action folder.
        val root = FileUtil.createTempDirectory("hygen-from-test", "")
        val templates = File(root, "_templates")
        val action = File(templates, "gen/deep/action").apply { mkdirs() }
        val template = File(action, "hello.ejs.t").apply {
            writeText("---\nto: app/x.js\nfrom: lib/deep/x.js\n---\nbody\n")
        }
        val vf = LocalFileSystem.getInstance().refreshAndFindFileByPath(template.absolutePath)!!
        val file = PsiManager.getInstance(project).findFile(vf) as HygenFile
        val warns = run(HygenFromValueInspection(), file).warningsContaining(fromMsg)
        assertEquals(1, warns.size)
        assertTrue(warns.single().descriptionTemplate.contains(File(templates, "lib/deep/x.js").path))
        FileUtil.delete(root)
    }
}
