package com.biorate.hygen.context

import com.biorate.hygen.lang.HygenLanguage
import com.biorate.hygen.lang.psi.HygenEjsIsland
import com.biorate.hygen.lang.psi.HygenFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assume.assumeTrue
import java.io.File

/**
 * Context-resolution sanity sweep (task 4.1): [HygenTemplateContext.visibleAt] must never throw and
 * must always return a non-empty context (the engine defaults at minimum) on real-world
 * `application-template` templates (the full `*.t` corpus).
 *
 * The entire real `*.t` corpus is parsed through [PsiFileFactory.createFileFromText] — the
 * same deterministic harness the sibling corpus sweeps use — and [HygenTemplateContext.visibleAt]
 * is called at every EJS island offset. The in-memory harness deliberately resolves no config (an
 * in-memory file has no `VirtualFile`, so `actionDir` degrades to null), which is exactly the
 * engine-defaults-only path the spec describes: it proves the resolver merges the always-present
 * `name`/`Name`/`names`/`Names`/`h` context over real template content and never throws on the
 * sequential in-island declaration scan (`const X = ...`, bare `X = ...`).
 *
 * The disk-backed config/hook resolution (nearest `.hygen.js`, prompt hook) is exercised separately
 * by [HygenTemplateContextTest] — [HygenTemplateContextTest.testHelpersFromRealApplicationTemplateConfig]
 * parses the real `application-template/.hygen.js`, and
 * [HygenTemplateContextTest.testConfigChangeInvalidatesCache] drives `visibleAt` against a real
 * on-disk config through the real VFS.
 *
 * The check SKIPS gracefully (JUnit `assumeTrue`) when the corpus directory is not present on this
 * machine, mirroring the sibling [com.biorate.hygen.lang.injection.HygenEjsFileInjectorTest]
 * corpus sweep.
 */
class HygenTemplateContextRealTemplatesTest : BasePlatformTestCase() {

    fun testVisibleAtNeverThrowsAndAlwaysHasEngineDefaults() {
        val root = File("/www/biorate/application-template/_templates")
        assumeTrue("application-template corpus not present on disk; skipping context sweep", root.isDirectory)

        val templates = root.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".t") }
            .sortedBy { it.path }
            .toList()
        assertTrue("no real *.t templates under ${root.path}", templates.isNotEmpty())

        var probedIslands = 0
        for (template in templates) {
            val file = PsiFileFactory.getInstance(project)
                .createFileFromText(template.name, HygenLanguage, template.readText()) as HygenFile

            val islands = PsiTreeUtil.collectElementsOfType(file, HygenEjsIsland::class.java)
            // Probe each island, plus offset 0 so island-less files are still exercised.
            val probes = islands.map { it.textRange.startOffset }.toMutableList().apply { add(0) }
            for (offset in probes) {
                val context = HygenTemplateContext.visibleAt(file, offset)
                assertFalse("visibleAt(${template.path}, $offset) returned empty context", context.isEmpty)
                assertTrue(
                    "visibleAt(${template.path}, $offset) missing engine default name variant",
                    setOf("name", "Name", "names", "Names", "h").all { it in context.variables },
                )
            }
            probedIslands += islands.size
        }
        assertTrue("no EJS islands probed in the corpus", probedIslands > 0)
    }
}
