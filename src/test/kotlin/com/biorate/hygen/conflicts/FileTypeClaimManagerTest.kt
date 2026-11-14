package com.biorate.hygen.conflicts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [FileTypeClaimManager] (tasks §1.2) against a fake [ExactFileNameAssociations]
 * — state, lifecycle (add/remove) and the persist-form canonicalization, no IDE required.
 */
class FileTypeClaimManagerTest {

    /** Fake exact-name seam recording every association attempt. */
    private class FakeAssociations : ExactFileNameAssociations {
        val associated = LinkedHashSet<String>()
        val removed = mutableListOf<String>()
        var fail = false

        override fun associateExact(name: String): Boolean {
            if (fail) return false
            associated += name
            return true
        }

        override fun removeExact(name: String): Boolean {
            if (fail) return false
            removed += name
            return true
        }
    }

    private fun manager(associations: FakeAssociations) = FileTypeClaimManager().apply {
        this.associations = associations
    }

    @Test
    fun `claim records the name and associates it exactly`() {
        val associations = FakeAssociations()
        val claims = manager(associations)

        assertTrue(claims.claim(".env.t"))

        assertEquals(setOf(".env.t"), claims.claimed())
        assertEquals(setOf(".env.t"), associations.associated)
        assertTrue(associations.removed.isEmpty())
    }

    @Test
    fun `claim is idempotent - the second claim of the same name reports no change`() {
        val associations = FakeAssociations()
        val claims = manager(associations)

        assertTrue(claims.claim(".env.t"))
        assertFalse(claims.claim(".env.t"))

        assertEquals(setOf(".env.t"), claims.claimed())
        // The association attempt happens only once.
        assertEquals(setOf(".env.t"), associations.associated)
    }

    @Test
    fun `release removes a claimed name and its exact association`() {
        val associations = FakeAssociations()
        val claims = manager(associations)
        claims.claim(".env.t")

        assertTrue(claims.release(".env.t"))

        assertTrue(claims.claimed().isEmpty())
        assertEquals(listOf(".env.t"), associations.removed)
    }

    @Test
    fun `release never touches names that are not in the plugin's own state`() {
        val associations = FakeAssociations()
        val claims = manager(associations)
        claims.claim(".env.t")

        assertFalse(claims.release("user-added.t"))

        // The user's own exact name is neither forgotten nor de-associated.
        assertEquals(setOf(".env.t"), claims.claimed())
        assertTrue(associations.removed.isEmpty())
    }

    @Test
    fun `claim and release canonicalize the name`() {
        val associations = FakeAssociations()
        val claims = manager(associations)

        assertTrue(claims.claim("  .env.t "))
        assertEquals(setOf(".env.t"), claims.claimed())

        assertFalse(claims.claim(""))
        assertFalse(claims.claim("  "))
        assertFalse(claims.claim("*.env.t"))
        assertFalse(claims.claim("a/b.env.t"))
        assertEquals(setOf(".env.t"), claims.claimed())
    }

    @Test
    fun `association failures do not lose the claim`() {
        val associations = FakeAssociations().apply { fail = true }
        val claims = manager(associations)

        // Claim-back is best effort: the seam reported failure, the name is still persisted so a
        // later sync (or the user) can re-apply it.
        assertTrue(claims.claim(".env.t"))
        assertEquals(setOf(".env.t"), claims.claimed())

        assertTrue(claims.release(".env.t"))
        assertTrue(claims.claimed().isEmpty())
    }

    @Test
    fun `state round-trip canonicalizes loaded names`() {
        val state = FileTypeClaimManager().apply {
            claimedFileNames = linkedSetOf(".env.t", "", "  ", "*.bad", "b.tmpl", ".env.t")
        }

        val claims = FileTypeClaimManager()
        claims.loadState(state)

        assertEquals(setOf(".env.t", "b.tmpl"), claims.claimed())
    }

    @Test
    fun `canonical rejects unusable names`() {
        assertNull(FileTypeClaimManager.canonical(""))
        assertNull(FileTypeClaimManager.canonical("  "))
        assertNull(FileTypeClaimManager.canonical("*.t"))
        assertNull(FileTypeClaimManager.canonical("dir/x.t"))
        assertEquals(".env.t", FileTypeClaimManager.canonical(" .env.t "))
    }

    @Test
    fun `claimed returns a defensive copy`() {
        val claims = manager(FakeAssociations())
        claims.claim(".env.t")

        (claims.claimed() as? MutableSet)?.clear()

        assertEquals(setOf(".env.t"), claims.claimed())
    }
}
