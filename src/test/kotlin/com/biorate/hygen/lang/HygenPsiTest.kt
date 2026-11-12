package com.biorate.hygen.lang

import com.biorate.hygen.lang.psi.HygenBody
import com.biorate.hygen.lang.psi.HygenEjsIsland
import com.biorate.hygen.lang.psi.HygenFile
import com.biorate.hygen.lang.psi.HygenFrontmatterKeyValue
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.psi.impl.DebugUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class HygenPsiTest : BasePlatformTestCase() {

    private fun configure(text: String): HygenFile =
        com.intellij.psi.PsiFileFactory.getInstance(project)
            .createFileFromText("hello.ejs.t", HygenLanguage, text) as HygenFile

    fun testFileTypeIsRegisteredForEjsTemplates() {
        assertEquals(HygenFileType, FileTypeManager.getInstance().getFileTypeByExtension("ejs.t"))
        assertEquals("ejs.t", HygenFileType.defaultExtension)
        assertTrue(HygenFileType is com.intellij.openapi.fileTypes.LanguageFileType)
    }

    fun testPsiTreeShape() {
        val file = configure(
            """
            |---
            |to: app/<%= name %>.js
            |inject: true
            |---
            |class <%= Name %> {}
            """.trimMargin(),
        )

        assertEquals(2, PsiTreeUtil.findChildrenOfType(file, HygenFrontmatterKeyValue::class.java).size)
        assertEquals(2, PsiTreeUtil.findChildrenOfType(file, HygenEjsIsland::class.java).size)
        assertNotNull(PsiTreeUtil.findChildOfType(file, HygenBody::class.java))
    }

    fun testToValueAccessor() {
        val file = configure(
            """
            |---
            |to: app/<%= section %>/<%= name %>.ts
            |---
            |export const x = 1
            """.trimMargin(),
        )
        assertEquals("app/<%= section %>/<%= name %>.ts", file.toValue)
    }

    fun testUnterminatedTagProducesNode() {
        val file = configure("---\nto: x\n---\nhello <%= name")
        assertTrue(DebugUtil.psiToString(file, false).contains("HYGEN_EJS_UNTERMINATED"))
    }

    fun testBodyOnlyFile() {
        val file = configure("plain <%= x %> text")
        assertNull(file.toValue)
        assertEquals(1, PsiTreeUtil.findChildrenOfType(file, HygenEjsIsland::class.java).size)
    }
}
