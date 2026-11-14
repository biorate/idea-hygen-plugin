package com.biorate.hygen.conflicts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the pure [representativeName] helper (tasks §1.1).
 */
class RepresentativeNameTest {

    @Test
    fun `simple wildcard maps to probe with same suffix`() {
        assertEquals("probe.t", representativeName("*.t"))
    }

    @Test
    fun `compound wildcard keeps the full suffix`() {
        assertEquals("probe.ejs.t", representativeName("*.ejs.t"))
        assertEquals("probe.tmpl", representativeName("*.tmpl"))
    }

    @Test
    fun `case is normalized to lower case`() {
        assertEquals("probe.t", representativeName("*.T"))
        assertEquals("probe.ejs.t", representativeName(" *.EJS.T "))
    }

    @Test
    fun `bare extension tokens are normalized to wildcard form first`() {
        assertEquals("probe.t", representativeName("t"))
        assertEquals("probe.ejs.t", representativeName("ejs.t"))
    }

    @Test
    fun `invalid patterns yield null`() {
        assertNull(representativeName(""))
        assertNull(representativeName("   "))
        assertNull(representativeName("abc"))
        assertNull(representativeName("*"))
        assertNull(representativeName("*."))
        assertNull(representativeName("*.t*"))
        assertNull(representativeName("*a.t"))
        assertNull(representativeName("a/b/*.t"))
        assertNull(representativeName("*."))
        assertNull(representativeName("*..t"))
        assertNull(representativeName("*.t."))
    }
}
