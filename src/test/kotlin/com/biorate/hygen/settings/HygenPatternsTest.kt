package com.biorate.hygen.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Unit tests for [HygenPatterns] (design D3): normalization, validation and matching of
 * user-configurable template filename patterns.
 *
 * [BasePlatformTestCase] is used because [HygenPatterns.matches] delegates to the platform's
 * [org.jetbrains.jps.model.fileTypes.FileNameMatcherFactory], the exact
 * [com.intellij.openapi.fileTypes.WildcardFileNameMatcher] the IDE installs for a file-type
 * `patterns=` association (see [com.biorate.hygen.lang.HygenFileTypeOverriderTest]).
 */
class HygenPatternsTest : BasePlatformTestCase() {

    // --- default / presets ---------------------------------------------------------------

    fun testDefaultAndPresets() {
        assertEquals("*.t", HygenPatterns.DEFAULT)
        assertEquals(listOf("*.t", "*.ejs.t", "*.tmpl"), HygenPatterns.PRESETS)
    }

    // --- normalize: valid inputs --------------------------------------------------------

    fun testNormalizeBareTokens() {
        assertEquals("*.t", HygenPatterns.normalize("t"))
        assertEquals("*.ejs.t", HygenPatterns.normalize("ejs.t"))
    }

    fun testNormalizeAlreadyWildcardPassesThrough() {
        assertEquals("*.t", HygenPatterns.normalize("*.t"))
        assertEquals("*.ejs.t", HygenPatterns.normalize("*.ejs.t"))
        assertEquals("*.tmpl", HygenPatterns.normalize("*.tmpl"))
    }

    fun testNormalizeIsIdempotent() {
        for (raw in listOf("t", "ejs.t", "*.t", "*.ejs.t", "*.tmpl")) {
            val once = HygenPatterns.normalize(raw)
            assertNotNull("must normalize $raw", once)
            assertEquals("normalize must be idempotent for $raw", once, HygenPatterns.normalize(once!!))
        }
    }

    fun testNormalizeTrimsAndLowercases() {
        assertEquals("*.t", HygenPatterns.normalize("  T "))
        assertEquals("*.ejs.t", HygenPatterns.normalize(" EJS.T "))
        assertEquals("*.t", HygenPatterns.normalize("  *.T  "))
    }

    // --- normalize: invalid inputs ------------------------------------------------------

    fun testNormalizeRejectsInvalidInputs() {
        for (raw in listOf("", "   ", "abc", "a*b.t", "path/*.t", "path/abc", "*", "*.", "**.t", "*.t*")) {
            assertNull("normalize($raw) must be null (invalid)", HygenPatterns.normalize(raw))
        }
    }

    // --- validate: errors ---------------------------------------------------------------

    fun testValidateAcceptsValidPatterns() {
        for (raw in listOf("t", "ejs.t", "*.t", "*.ejs.t", "*.tmpl", " EJS.T ")) {
            assertNull("validate($raw) must pass", HygenPatterns.validate(raw))
        }
    }

    fun testValidateRejectsEmpty() {
        val error = HygenPatterns.validate("")
        assertNotNull("empty entry must be rejected", error)
        assertTrue("empty error should mention empty, was: $error", error!!.contains("not be empty"))

        assertNotNull("blank entry must be rejected", HygenPatterns.validate("   "))
    }

    fun testValidateRejectsBareWord() {
        val error = HygenPatterns.validate("abc")
        assertNotNull("bare word abc must be rejected", error)
        assertTrue("error should mention wildcard/suffix form, was: $error", error!!.contains("wildcard/suffix"))
    }

    fun testValidateRejectsMidStringWildcards() {
        val error = HygenPatterns.validate("a*b.t")
        assertNotNull("mid-string wildcard must be rejected", error)

        assertNotNull("double leading wildcard must be rejected", HygenPatterns.validate("**.t"))
        assertNotNull("trailing wildcard must be rejected", HygenPatterns.validate("*.t*"))
    }

    fun testValidateRejectsPathSeparators() {
        val error = HygenPatterns.validate("path/*.t")
        assertNotNull("slash-bearing pattern must be rejected", error)
        assertTrue("error should mention path separator, was: $error", error!!.contains("path separator"))
    }

    // --- matches: pattern semantics -----------------------------------------------------

    fun testDefaultPatternMatchesTNames() {
        assertTrue(HygenPatterns.matches("*.t", "report.t"))
        assertTrue(HygenPatterns.matches("*.t", "00009.00_x.t"))
        assertFalse(HygenPatterns.matches("*.t", "report.tx"))
        assertFalse(HygenPatterns.matches("*.t", "component.js"))
        // A compound `.ejs.t` name is covered by the single leading `*` prefix wildcard (design
        // D4): the default `*.t` setting must claim it, so no separate `*.ejs.t` association is
        // required for the conventional templates.
        assertTrue(HygenPatterns.matches("*.t", "x.ejs.t"))
        // ...but it must NOT sweep up unrelated suffixes: `*.tmpl` is a different setting.
        assertFalse("the default *.t setting must not claim a .tmpl template", HygenPatterns.matches("*.t", "x.tmpl"))
    }

    fun testDefaultSettingsDriveFileResolution() {
        // The resolution path is settings-driven: whatever patterns are stored (default `*.t`)
        // are what HygenPatterns.matches accepts, and the associator installs exactly those. The
        // `00009.00_x.t` name is the canonical real-world template name from the corpus sweep.
        val defaultPatterns = HygenTemplateSettings().patterns.toList()
        assertEquals(listOf(HygenPatterns.DEFAULT), defaultPatterns)
        assertTrue(HygenPatterns.matches(defaultPatterns.single(), "00009.00_x.t"))
        assertTrue(HygenPatterns.matches(defaultPatterns.single(), "x.ejs.t"))
        assertFalse(HygenPatterns.matches(defaultPatterns.single(), "x.tmpl"))
    }

    fun testCompoundSuffixStillMatchesCompound() {
        assertTrue(HygenPatterns.matches("*.ejs.t", "00009.00_.prettierrc.ejs.t"))
        assertTrue(HygenPatterns.matches("*.ejs.t", "component.ejs.t"))
        assertFalse("a plain .t file must not match *.ejs.t", HygenPatterns.matches("*.ejs.t", "component.t"))
    }

    fun testTmplMatchesSuffixButNotLongerSuffix() {
        assertTrue(HygenPatterns.matches("*.tmpl", "c.tmpl"))
        assertFalse("c.tmplx must not match *.tmpl", HygenPatterns.matches("*.tmpl", "c.tmplx"))
    }

    fun testMatchesIsConsistentWithNormalization() {
        // Every normalized pattern must be accepted by the platform matcher.
        assertTrue(HygenPatterns.matches(HygenPatterns.normalize("t")!!, "report.t"))
        assertTrue(HygenPatterns.matches(HygenPatterns.normalize("ejs.t")!!, "component.ejs.t"))
        assertTrue(HygenPatterns.matches(HygenPatterns.normalize("*.tmpl")!!, "component.tmpl"))
    }
}
