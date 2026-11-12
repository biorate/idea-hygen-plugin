package com.biorate.hygen.lang

import com.intellij.testFramework.fixtures.BasePlatformTestCase

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
}
