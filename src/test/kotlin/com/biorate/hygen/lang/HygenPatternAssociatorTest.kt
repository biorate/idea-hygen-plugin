package com.biorate.hygen.lang

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [HygenPatternAssociator] (design D1/D5): reconciling the [HygenFileType]
 * associations through a recording fake gateway verifies the exact associate/remove sequences
 * without the IDE.
 */
class HygenPatternAssociatorTest {

    /** Recording fake [HygenAssociationGateway] that mirrors a real file-type manager. */
    private class FakeGateway(seed: Set<String> = emptySet()) : HygenAssociationGateway {
        private val current = seed.toMutableSet()
        val callLog = mutableListOf<String>()

        override fun currentPatterns(): Set<String> = current.toSet()

        override fun associate(pattern: String) {
            callLog += "associate:$pattern"
            current += pattern
        }

        override fun remove(pattern: String) {
            callLog += "remove:$pattern"
            current -= pattern
        }
    }

    @Test
    fun `pattern add associates the new pattern`() {
        val gateway = FakeGateway(seed = setOf("*.t"))
        HygenPatternAssociator(gateway).sync(listOf("*.t", "*.tmpl"))

        assertEquals(listOf("associate:*.tmpl"), gateway.callLog)
    }

    @Test
    fun `pattern removal removes the stale association`() {
        val gateway = FakeGateway(seed = setOf("*.t", "*.tmpl"))
        HygenPatternAssociator(gateway).sync(listOf("*.t"))

        assertEquals(listOf("remove:*.tmpl"), gateway.callLog)
    }

    @Test
    fun `full re-sync removes stale and adds new`() {
        val gateway = FakeGateway(seed = setOf("*.ejs.t", "*.tmpl"))
        HygenPatternAssociator(gateway).sync(listOf("*.t"))

        assertEquals(
            listOf("remove:*.ejs.t", "remove:*.tmpl", "associate:*.t"),
            gateway.callLog,
        )
    }

    @Test
    fun `idempotent double run produces no duplicate calls`() {
        val gateway = FakeGateway()
        val associator = HygenPatternAssociator(gateway)

        associator.sync(listOf("*.t", "*.tmpl"))
        val afterFirst = gateway.callLog.toList()
        assertEquals(2, afterFirst.size)

        // The gateway state already matches the desired configuration, so a second sync is a no-op.
        associator.sync(listOf("*.t", "*.tmpl"))
        assertEquals(afterFirst, gateway.callLog)
    }

    @Test
    fun `invalid patterns are dropped from the desired set`() {
        val gateway = FakeGateway()
        HygenPatternAssociator(gateway).sync(listOf("*.t", "abc", "", "  "))

        assertEquals(listOf("associate:*.t"), gateway.callLog)
    }

    @Test
    fun `bare extension tokens are normalized before associating`() {
        val gateway = FakeGateway()
        HygenPatternAssociator(gateway).sync(listOf("t", "ejs.t"))

        assertEquals(listOf("associate:*.t", "associate:*.ejs.t"), gateway.callLog)
    }

    @Test
    fun `no-op sync on identical configuration`() {
        val gateway = FakeGateway(seed = setOf("*.ejs.t"))
        HygenPatternAssociator(gateway).sync(listOf("*.ejs.t"))

        assertTrue("no calls expected when already in sync", gateway.callLog.isEmpty())
    }
}
