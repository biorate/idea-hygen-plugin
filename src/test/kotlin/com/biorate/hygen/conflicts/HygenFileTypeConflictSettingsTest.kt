package com.biorate.hygen.conflicts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the persistent suppress state (tasks §2.1). The component is exercised directly
 * (construct + loadState), the same style as [com.biorate.hygen.settings.HygenTemplateSettings]
 * tests — no running application required.
 */
class HygenFileTypeConflictSettingsTest {

    @Test
    fun `default state has no suppressions`() {
        val settings = HygenFileTypeConflictSettings()

        assertTrue(settings.suppressedFileTypes.isEmpty())
        assertFalse(settings.isSuppressed(".env file"))
    }

    @Test
    fun `suppress registers the type and isSuppressed reflects it`() {
        val settings = HygenFileTypeConflictSettings()
        settings.suppress(".env file")

        assertTrue(settings.isSuppressed(".env file"))
        assertEquals(setOf(".env file"), settings.suppressedFileTypes)
    }

    @Test
    fun `suppress is idempotent and ignores blank names`() {
        val settings = HygenFileTypeConflictSettings()
        settings.suppress(".env file")
        settings.suppress(".env file")
        settings.suppress("  ")
        settings.suppress("")

        assertEquals(setOf(".env file"), settings.suppressedFileTypes)
    }

    @Test
    fun `state round-trips through getState and loadState`() {
        val original = HygenFileTypeConflictSettings()
        original.suppress(".env file")
        original.suppress("Perl")

        val restored = HygenFileTypeConflictSettings()
        restored.loadState(original.state)

        assertEquals(setOf(".env file", "Perl"), restored.suppressedFileTypes)
        assertTrue(restored.isSuppressed("Perl"))
    }

    @Test
    fun `loadState canonicalizes what is read from disk`() {
        val restored = HygenFileTypeConflictSettings()
        val dirty = HygenFileTypeConflictSettings().apply {
            suppressedFileTypes = mutableSetOf("b type", "b type", "a type", " ", "")
        }

        restored.loadState(dirty)

        assertEquals(listOf("a type", "b type"), restored.suppressedFileTypes.sorted())
    }

    @Test
    fun `loadState replaces rather than merges`() {
        val restored = HygenFileTypeConflictSettings().apply { suppress("old type") }
        val replacement = HygenFileTypeConflictSettings().apply { suppress("new type") }

        restored.loadState(replacement.state)

        assertFalse(restored.isSuppressed("old type"))
        assertTrue(restored.isSuppressed("new type"))
    }
}
