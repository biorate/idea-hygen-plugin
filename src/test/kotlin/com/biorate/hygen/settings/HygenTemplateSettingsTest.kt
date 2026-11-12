package com.biorate.hygen.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [HygenTemplateSettings] canonicalization (design D2): the list stored and loaded
 * is always normalized, distinct and sorted, with invalid entries dropped. [canonicalize] is a
 * pure companion function, so it is exercised without the IDE.
 */
class HygenTemplateSettingsTest {

    @Test
    fun `defaults to the t wildcard when nothing is stored`() {
        val settings = HygenTemplateSettings()
        assertEquals(listOf(HygenPatterns.DEFAULT), settings.patterns)
    }

    @Test
    fun `canonicalize normalizes bare tokens and drops invalid`() {
        val raw = listOf("ejs.t", "abc", " T ", "", "*.tmpl", "*.t")
        val result = HygenTemplateSettings.canonicalize(raw)
        assertEquals(listOf("*.ejs.t", "*.t", "*.tmpl"), result)
    }

    @Test
    fun `canonicalize is distinct sorted and case-folded`() {
        val raw = listOf("*.t", "*.T", "*.tmpl", "*.tmpl")
        val result = HygenTemplateSettings.canonicalize(raw)
        assertEquals(listOf("*.t", "*.tmpl"), result)
    }

    @Test
    fun `canonicalize preserves an empty list`() {
        // design D4: an empty list disables extension-based claiming entirely.
        assertTrue(HygenTemplateSettings.canonicalize(emptyList()).isEmpty())
        assertTrue(HygenTemplateSettings.canonicalize(listOf("", "   ")).isEmpty())
    }
}
