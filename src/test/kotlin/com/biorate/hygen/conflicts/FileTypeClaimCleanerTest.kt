package com.biorate.hygen.conflicts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [FileTypeClaimCleaner] (tasks §2.2): claim cleanup at sync start, on fakes.
 */
class FileTypeClaimCleanerTest {

    private val hygen = "Hygen Template"

    private class FakeProbe(private val resolution: Map<String, String>) : FileTypeProbe {
        override fun getFileTypeByFileName(fileName: String): String? = resolution[fileName]
    }

    private class FakeClaims(initial: Set<String>) : FileTypeClaims {
        val stored = initial.toMutableSet()
        val released = mutableListOf<String>()

        override fun claim(name: String): Boolean = stored.add(name)
        override fun claimed(): Set<String> = LinkedHashSet(stored)
        override fun release(name: String): Boolean {
            if (stored.remove(name)) {
                released += name
                return true
            }
            return false
        }
    }

    private fun cleaner(
        claims: FakeClaims,
        resolution: Map<String, String> = emptyMap(),
    ) = FileTypeClaimCleaner(claims, FakeProbe(resolution), ourFileTypeName = hygen)

    @Test
    fun `claims still matching our patterns and resolving to our type are kept`() {
        val claims = FakeClaims(setOf(".env.t"))
        cleaner(claims, resolution = mapOf(".env.t" to hygen))
            .cleanup(listOf("*.t"))

        assertTrue(claims.released.isEmpty())
    }

    @Test
    fun `claims are released when their file no longer exists`() {
        val claims = FakeClaims(setOf(".env.t"))
        cleaner(claims, resolution = mapOf(".env.t" to hygen))
            .cleanup(listOf("*.t"), existence = { false })

        assertEquals(listOf(".env.t"), claims.released)
    }

    @Test
    fun `claims are released when the pattern was removed from the settings`() {
        val claims = FakeClaims(setOf(".env.t"))
        cleaner(claims, resolution = mapOf(".env.t" to hygen))
            .cleanup(listOf("*.tmpl"))

        assertEquals(listOf(".env.t"), claims.released)
    }

    @Test
    fun `claims are released when the exact association no longer resolves to our type`() {
        val claims = FakeClaims(setOf(".env.t"))
        cleaner(claims, resolution = mapOf(".env.t" to ".env file"))
            .cleanup(listOf("*.t"))

        assertEquals(listOf(".env.t"), claims.released)
    }

    @Test
    fun `an unavailable probe lookup keeps the claim - cleanup never releases on a failed check`() {
        val claims = FakeClaims(setOf(".env.t"))
        cleaner(claims, resolution = emptyMap()).cleanup(listOf("*.t"))

        assertTrue(claims.released.isEmpty())
    }

    @Test
    fun `cleanup releases only the stale claims and keeps the healthy ones`() {
        val claims = FakeClaims(setOf(".env.t", "kept.tmpl"))
        cleaner(
            claims,
            resolution = mapOf(".env.t" to ".env file", "kept.tmpl" to hygen),
        ).cleanup(listOf("*.t", "*.tmpl"), existence = { it == "kept.tmpl" })

        assertEquals(listOf(".env.t"), claims.released)
        assertEquals(setOf("kept.tmpl"), claims.claimed())
    }

    @Test
    fun `releasing a claim the store no longer has is not reported as released`() {
        val claims = FakeClaims(emptySet())

        assertTrue(cleaner(claims).cleanup(listOf("*.t")).isEmpty())
    }
}
