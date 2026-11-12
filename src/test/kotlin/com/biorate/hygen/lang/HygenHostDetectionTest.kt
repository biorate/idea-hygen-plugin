package com.biorate.hygen.lang

import com.intellij.lang.Language
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Unit tests for [HygenHostDetection.detectHost], one per scenario of the
 * `hygen-host-detection` spec. Language resolution goes through the platform file-type
 * registry, so this is a platform test case.
 */
class HygenHostDetectionTest : BasePlatformTestCase() {

    private fun hostId(to: String?, fileName: String = "hello.ejs.t"): String? =
        HygenHostDetection.detectHost(to, fileName)?.id

    fun testStaticTargetExtension() {
        // spec: "Static target extension" — to: app/emails/report.js -> JavaScript
        assertEquals("JavaScript", hostId("app/emails/report.js"))
    }

    fun testInterpolatedPathSegments() {
        // spec: "Interpolated path segments" — to: app/<%= section %>/<%= name %>.ts -> TypeScript
        assertEquals("TypeScript", hostId("app/<%= section %>/<%= name %>.ts"))
    }

    fun testFullyDynamicTargetFallsBackToFileName() {
        // spec: "Fully dynamic target" — no extractable extension -> filename convention
        assertEquals("JavaScript", hostId("app/<%= fileName %>", "hello.js.ejs.t"))
    }

    fun testFullyDynamicTargetWithPlainFileNameIsPlainText() {
        // to: yields no extension and the filename convention yields none either -> plain text
        assertNull(hostId("app/<%= fileName %>", "hello.ejs.t"))
    }

    fun testConventionalSuffixedFileName() {
        // spec: "Conventional suffixed filename" — hello.js.ejs.t -> JavaScript
        assertEquals("JavaScript", hostId(null, "hello.js.ejs.t"))
    }

    fun testUnconventionalFileNameIsPlainText() {
        // spec: "Unconventional filename" — hello.ejs.t with no `to:` -> plain text
        assertNull(hostId(null, "hello.ejs.t"))
    }

    fun testUnsupportedExtensionDegradesToPlainText() {
        // spec: "Unsupported extension" — no registered language -> plain text, no error
        assertNull(hostId("app/emails/report.xyzzy"))
        // An extension that *is* extractable but unknown does not fall back to the file name:
        // the `to:` value is authoritative once it yields an extension.
        assertNull(hostId("app/emails/report.xyzzy", "hello.js.ejs.t"))
    }

    fun testInterpolatedExtensionIsNotExtractableAndFallsBack() {
        // `to: app/<%= name %>.<%= ext %>` — the extension itself is dynamic -> filename wins
        assertEquals("JavaScript", hostId("app/<%= name %>.<%= ext %>", "hello.js.ejs.t"))
    }

    fun testLastPathSegmentWinsOverEarlierSegments() {
        // a dot in an earlier segment must not be mistaken for the target extension
        assertEquals("JavaScript", hostId("app/v1.0/report.js"))
        assertNull(hostId("app/v1.0/report"))
    }

    fun testJavaScriptAndTypeScriptLanguagesAreAvailableInTestRuntime() {
        // Guards the JavaScript/TypeScript scenarios above: without the bundled JavaScript
        // plugin the platform cannot resolve those languages and the tests would degrade.
        assertNotNull(Language.findLanguageByID("JavaScript"))
        assertNotNull(Language.findLanguageByID("TypeScript"))
    }
}
