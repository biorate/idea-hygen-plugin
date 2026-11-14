package com.biorate.hygen.lang

import com.intellij.openapi.fileTypes.FileNameMatcher
import com.intellij.openapi.fileTypes.FileTypeManager
import com.biorate.hygen.settings.HygenPatterns
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.jps.model.fileTypes.FileNameMatcherFactory

/**
 * Verifies the file-type classification (spec requirement "Hook files are not templates"):
 * the [HygenFileTypeOverrider] must leave the engine hook files (`hooks.*`, `prompt.*`,
 * `index.*` — the engine's render `ignores` set) as ordinary JS/TS files (no template
 * parsing), while still claiming non-hook files under a `_templates` directory as hygen
 * templates.
 */
class HygenFileTypeOverriderTest : BasePlatformTestCase() {

    private fun add(path: String) =
        myFixture.addFileToProject(path, "// content").virtualFile

    fun testHookFilesAreNotClaimedAsTemplates() {
        val overrider = HygenFileTypeOverrider()
        for (name in HookFiles.NAMES) {
            val vf = add("_templates/mygen/new/$name")
            assertTrue("fixture path missing _templates: ${vf.path}", vf.path.contains("/_templates/"))
            assertNull("hook file $name must not become a hygen template", overrider.getOverriddenFileType(vf))
        }
    }

    // Regression for hooks-filetype-overrider-parity: the engine's render `ignores` also covers
    // `hooks.ts|js|mjs|cjs` (upstream 96eadd896); the overrider previously hijacked `hooks.ts`,
    // which broke the IDE's TypeScript service for it.
    fun testEngineHookNamesAreNotClaimedAsTemplates() {
        val overrider = HygenFileTypeOverrider()
        for (name in listOf("hooks.ts", "hooks.js", "hooks.mjs", "hooks.cjs")) {
            val vf = add("_templates/create/root/$name")
            assertTrue("fixture path missing _templates: ${vf.path}", vf.path.contains("/_templates/"))
            assertNull("engine hook file $name must not become a hygen template", overrider.getOverriddenFileType(vf))
        }
    }

    fun testHookFilesOutsideTemplatesAreUnaffected() {
        val overrider = HygenFileTypeOverrider()
        val vf = add("src/hooks.ts")
        assertNull("hooks.ts outside _templates must not be touched by the overrider", overrider.getOverriddenFileType(vf))
    }

    fun testNonHookTemplateUnderTemplatesIsClaimed() {
        val overrider = HygenFileTypeOverrider()
        val vf = add("_templates/mygen/new/component.js")
        assertEquals(HygenFileType, overrider.getOverriddenFileType(vf))
    }

    fun testFilesOutsideTemplatesAreLeftAlone() {
        val overrider = HygenFileTypeOverrider()
        val vf = add("src/component.js")
        assertNull("file outside _templates must not become a hygen template", overrider.getOverriddenFileType(vf))
    }

    // --- `.t` extension matching ---------------------------------------------------------

    fun testPatternMatchesTNames() {
        // Mirrors exactly what the default `*.t` association installs: a FileNameMatcher applied
        // to the whole file name (design D1/D4; the `patterns` attribute is no longer static).
        val matcher: FileNameMatcher =
            FileNameMatcherFactory.getInstance().createMatcher(HygenPatterns.DEFAULT)

        assertTrue(
            "a name ending in .t must resolve to the hygen template type",
            matcher.acceptsCharSequence("00009.00_.prettierrc.ejs.t"),
        )
        assertTrue("a simple .t name must match", matcher.acceptsCharSequence("component.t"))
        assertTrue("a plain .t file must be claimed", matcher.acceptsCharSequence("plain.t"))
        assertFalse("a file with a .t-ish tail must not match", matcher.acceptsCharSequence("x.ejs.tx"))
    }

    fun testTFileResolvesToHygenTypeByName() {
        // Name-based resolution exercises the same `findAssociatedFileType` path the IDE uses
        // for `.t` files. The pattern is global, so no `_templates` directory is required.
        val type = FileTypeManager.getInstance().getFileTypeByFileName("00009.00_.prettierrc.ejs.t")
        assertEquals(
            "a file named ...prettierrc.ejs.t must resolve to the hygen template type via the "
                + "registered *.t pattern",
            HygenFileType,
            type,
        )
    }

    fun testPlainTFileResolvesToHygenType() {
        val type = FileTypeManager.getInstance().getFileTypeByFileName("plain.t")
        assertEquals(
            "a plain .t file is intentionally claimed by the hygen plugin (taking over from Perl)",
            HygenFileType,
            type,
        )
    }
}
