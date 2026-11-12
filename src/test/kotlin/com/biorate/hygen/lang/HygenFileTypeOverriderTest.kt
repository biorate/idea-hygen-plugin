package com.biorate.hygen.lang

import com.intellij.openapi.fileTypes.FileNameMatcher
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.jps.model.fileTypes.FileNameMatcherFactory

/**
 * Verifies the file-type classification (spec requirement "Hook files are not templates"):
 * the [HygenFileTypeOverrider] must leave the hook files `prompt.js`/`index.js`/`prompt.ts`/
 * `index.ts` as ordinary JS/TS files (no template parsing), while still claiming non-hook
 * files under a `_templates` directory as hygen templates.
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

    // --- Compound `.ejs.t` extension matching -------------------------------------------

    fun testCompoundExtensionMatcherMatchesEjsTNamesOnly() {
        // Mirrors exactly what the `<fileType patterns="${HygenFileType.FILE_NAME_PATTERN}">`
        // registration installs: a FileNameMatcher applied to the whole file name. A compound
        // extension cannot be matched through the `extensions` attribute (which only looks at
        // the segment after the last dot), hence the dedicated pattern.
        val matcher: FileNameMatcher =
            FileNameMatcherFactory.getInstance().createMatcher(HygenFileType.FILE_NAME_PATTERN)

        assertTrue(
            "a name ending in .ejs.t must resolve to the hygen template type",
            matcher.acceptsCharSequence("00009.00_.prettierrc.ejs.t"),
        )
        assertTrue("a simple .ejs.t name must match", matcher.acceptsCharSequence("component.ejs.t"))
        assertFalse("a plain .t file must NOT be claimed (belongs to the Perl file type)", matcher.acceptsCharSequence("plain.t"))
        assertFalse("a .t file with a .ejs.t-ish tail must not match", matcher.acceptsCharSequence("x.ejs.tx"))
    }

    fun testCompoundExtensionFileResolvesToHygenTypeByName() {
        // Name-based resolution exercises the same `findAssociatedFileType` path the IDE uses
        // for `.ejs.t` files. The pattern is global, so no `_templates` directory is required.
        val type = FileTypeManager.getInstance().getFileTypeByFileName("00009.00_.prettierrc.ejs.t")
        assertEquals(
            "a file named ...prettierrc.ejs.t must resolve to the hygen template type via the "
                + "registered pattern, not fall through to a *.t plugin",
            HygenFileType,
            type,
        )
    }

    fun testPlainTFileDoesNotResolveToHygenType() {
        val type = FileTypeManager.getInstance().getFileTypeByFileName("plain.t")
        assertTrue(
            "a plain .t file must not be claimed by the hygen plugin (it belongs to Perl)",
            type !is HygenFileType,
        )
    }
}
