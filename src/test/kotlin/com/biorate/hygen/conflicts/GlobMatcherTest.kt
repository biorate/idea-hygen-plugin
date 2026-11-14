package com.biorate.hygen.conflicts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure [GlobMatcher] and [intersectionRepresentative] helpers used by the
 * intersection-based conflict detection.
 */
class GlobMatcherTest {

    @Test
    fun `exact patterns match only the exact name`() {
        assertTrue(GlobMatcher.matches("Makefile", "Makefile"))
        assertFalse(GlobMatcher.matches("Makefile", "makefile.am"))
        assertFalse(GlobMatcher.matches("Makefile", "Makefile2"))
    }

    @Test
    fun `star anywhere matches`() {
        assertTrue(GlobMatcher.matches("*.t", "probe.t"))
        assertTrue(GlobMatcher.matches("*.t", ".env.probe.t"))
        assertTrue(GlobMatcher.matches(".env.*", ".env.local"))
        assertTrue(GlobMatcher.matches(".env.*", ".env.probe.t"))
        assertTrue(GlobMatcher.matches("*.env.*", ".env.probe.t"))
        assertTrue(GlobMatcher.matches("a*b*c", "aXbYc"))
        assertTrue(GlobMatcher.matches("*.py", "script.py"))
    }

    @Test
    fun `matching is case-insensitive`() {
        assertTrue(GlobMatcher.matches("*.T", "Probe.T"))
        assertTrue(GlobMatcher.matches(".ENV.*", ".env.x"))
    }

    @Test
    fun `non-matches`() {
        assertFalse(GlobMatcher.matches("*.py", "probe.t"))
        assertFalse(GlobMatcher.matches("*.t", "probe.py"))
        assertFalse(GlobMatcher.matches(".env.*", "probe.env"))
        assertFalse(GlobMatcher.matches("a*b*c", "aXcYb"))
    }

    @Test
    fun `bare star matches everything`() {
        assertTrue(GlobMatcher.matches("*", "anything"))
    }
}

class IntersectionRepresentativeTest {

    @Test
    fun `foreign trailing wildcard gets our suffix`() {
        assertEquals(".env.probe.t", intersectionRepresentative("*.t", ".env.*"))
        assertEquals("probe.t", intersectionRepresentative("*.t", "*"))
    }

    @Test
    fun `foreign wildcard at both ends`() {
        assertEquals(".env.probe.t", intersectionRepresentative("*.t", "*.env.*"))
    }

    @Test
    fun `exact foreign name is used as is when it also matches our pattern`() {
        assertEquals("Gemfile.t", intersectionRepresentative("*.t", "Gemfile.t"))
    }

    @Test
    fun `no representable intersection yields null`() {
        assertNull(intersectionRepresentative("*.t", "*.py"))
        assertNull(intersectionRepresentative("*.t", "*.env"))
    }

    @Test
    fun `invalid our pattern yields null`() {
        assertNull(intersectionRepresentative("abc", "*.t"))
    }
}
