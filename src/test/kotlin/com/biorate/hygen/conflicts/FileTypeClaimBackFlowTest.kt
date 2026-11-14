package com.biorate.hygen.conflicts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the claim-back flows (tasks §3.1/§3.2): [FileTypeClaimBacker], the
 * claim-back action path of [FileTypeConflictController], and the reporter-level behaviour that
 * conflicts whose files are all already claimed never reach the aggregated balloon.
 */
class FileTypeClaimBackFlowTest {

    private val hygen = "Hygen Template"

    private class FakeProbe(private val resolution: Map<String, String>) : FileTypeProbe {
        override fun getFileTypeByFileName(fileName: String): String? = resolution[fileName]
    }

    private class FakeSuppression : ConflictSuppression {
        val suppressed = mutableSetOf<String>()
        override fun isSuppressed(fileTypeName: String): Boolean = fileTypeName in suppressed
        override fun suppress(fileTypeName: String) {
            suppressed += fileTypeName
        }
    }

    private class FakeClaims(initial: Set<String> = emptySet()) : FileTypeClaims {
        val stored = initial.toMutableSet()
        override fun claim(name: String): Boolean = stored.add(name)
        override fun claimed(): Set<String> = LinkedHashSet(stored)
        override fun release(name: String): Boolean = stored.remove(name)
    }

    private class FakeFinder(private val files: List<String>) : ConflictFileFinder {
        override fun find(ourPattern: String, foreignPattern: String, limit: Int): List<String> = files
    }

    private class RecordingReporter : ConflictReporter {
        var reported: List<FileTypeConflict>? = null
        var claimBackCallback: ((List<String>) -> Unit)? = null

        override fun report(
            conflicts: List<FileTypeConflict>,
            onSuppress: (String) -> Unit,
            onResolve: (List<FileTypeConflict>) -> Unit,
            onClaimBack: (List<String>) -> Unit,
        ) {
            reported = conflicts
            claimBackCallback = onClaimBack
        }
    }

    private val dotenv = ForeignFileType(".env file", listOf(".env.*"))

    private fun controller(
        resolution: Map<String, String>,
        reporter: RecordingReporter,
        claims: FakeClaims,
        files: List<String>,
    ): FileTypeConflictController {
        val resyncs = mutableListOf<Unit>()
        return FileTypeConflictController(
            detector = FileTypeConflictDetector(
                probe = FakeProbe(resolution),
                suppression = FakeSuppression(),
                foreignPatterns = ForeignPatterns { listOf(dotenv) },
                ourFileTypeName = hygen,
                realFiles = FakeFinder(files),
            ),
            suppression = FakeSuppression(),
            reporter = reporter,
            resolver = FileTypeConflictResolver({ _, _ -> false }, {}, { _, _ -> }),
            claimBacker = FileTypeClaimBacker(claims, resync = { resyncs += Unit }),
        )
    }

    @Test
    fun `claimBack claims the names and triggers one resync`() {
        val claims = FakeClaims()
        var resyncs = 0

        FileTypeClaimBacker(claims, resync = { resyncs++ }).claimBack(listOf(".env.t", "app.env.t"))

        assertEquals(setOf(".env.t", "app.env.t"), claims.claimed())
        assertEquals(1, resyncs)
    }

    @Test
    fun `claimBack with no new claims does not resync`() {
        val claims = FakeClaims(setOf(".env.t"))
        var resyncs = 0

        FileTypeClaimBacker(claims, resync = { resyncs++ }).claimBack(listOf(".env.t"))

        assertEquals(0, resyncs)
    }

    @Test
    fun `claimBack of an empty list does nothing`() {
        val claims = FakeClaims()
        var resyncs = 0

        FileTypeClaimBacker(claims, resync = { resyncs++ }).claimBack(emptyList())

        assertTrue(claims.claimed().isEmpty())
        assertEquals(0, resyncs)
    }

    @Test
    fun `claimBacker failures never propagate to the caller`() {
        val claims = object : FileTypeClaims {
            override fun claim(name: String): Boolean = throw IllegalStateException("state gone")
            override fun claimed(): Set<String> = emptySet()
            override fun release(name: String): Boolean = false
        }
        var resyncs = 0

        FileTypeClaimBacker(claims, resync = { resyncs++ }).claimBack(listOf(".env.t"))

        assertEquals(0, resyncs)
    }

    @Test
    fun `the controller wires the claim-back callback through to the claim backer`() {
        val reporter = RecordingReporter()
        val claims = FakeClaims()
        controller(
            resolution = mapOf(".env.probe.t" to ".env file", ".env.t" to ".env file"),
            reporter = reporter,
            claims = claims,
            files = listOf(".env.t"),
        ).run(listOf("*.t"))

        assertEquals(
            listOf(FileTypeConflict("*.t", ".env file", ".env.probe.t", listOf(".env.t"))),
            reporter.reported,
        )
        // Drive the callback as the confirmed dialog result would.
        reporter.claimBackCallback!!.invoke(listOf(".env.t"))

        assertEquals(setOf(".env.t"), claims.claimed())
    }

    @Test
    fun `a conflict without real files offers nothing to claim back`() {
        val reporter = RecordingReporter()
        controller(
            resolution = mapOf(".env.probe.t" to ".env file"),
            reporter = reporter,
            claims = FakeClaims(),
            files = emptyList(),
        ).run(listOf("*.t"))

        // Nothing claimed even if the (unconfirmed) action were invoked with all names.
        reporter.claimBackCallback!!.invoke(emptyList())
        assertTrue(reporter.reported!!.single().conflictingFileNames.isEmpty())
    }

    @Test
    fun `no claimBacker - the claim-back action degrades to a no-op`() {        val reporter = RecordingReporter()
        val controller = FileTypeConflictController(
            detector = FileTypeConflictDetector(
                probe = FakeProbe(mapOf(".env.probe.t" to ".env file")),
                suppression = FakeSuppression(),
                foreignPatterns = ForeignPatterns { listOf(dotenv) },
                ourFileTypeName = hygen,
            ),
            suppression = FakeSuppression(),
            reporter = reporter,
            resolver = FileTypeConflictResolver({ _, _ -> false }, {}, { _, _ -> }),
        )
        controller.run(listOf("*.t"))

        // The conflict is still reported, without claim-back names, and the (unused) action
        // degrades to a no-op instead of crashing.
        assertEquals(
            listOf(FileTypeConflict("*.t", ".env file", ".env.probe.t")),
            reporter.reported,
        )
        reporter.claimBackCallback!!.invoke(listOf(".env.t"))
    }
}

/**
 * Tasks §3.2, reporter-level: a conflict whose real files all resolve to the hygen type (already
 * claimed back) never reaches the aggregated balloon — asserted here end-to-end through the
 * controller, the same wiring the lifecycle installs.
 */
class FileTypeClaimBackReporterFilterTest {

    private val hygen = "Hygen Template"

    private class FakeProbe(private val resolution: Map<String, String>) : FileTypeProbe {
        override fun getFileTypeByFileName(fileName: String): String? = resolution[fileName]
    }

    private class FakeFinder(private val files: List<String>) : ConflictFileFinder {
        override fun find(ourPattern: String, foreignPattern: String, limit: Int): List<String> = files
    }

    private class RecordingReporter : ConflictReporter {
        var reported: List<FileTypeConflict>? = null

        override fun report(
            conflicts: List<FileTypeConflict>,
            onSuppress: (String) -> Unit,
            onResolve: (List<FileTypeConflict>) -> Unit,
            onClaimBack: (List<String>) -> Unit,
        ) {
            reported = conflicts
        }
    }

    @Test
    fun `a fully claimed-back pair is filtered out of the aggregated balloon`() {
        val reporter = RecordingReporter()
        // Claimed-back state: `.env.t` resolves to hygen via its exact-name association, while
        // the representative probe (no real file for it) still lands on the foreign type.
        FileTypeConflictController(
            detector = FileTypeConflictDetector(
                probe = FakeProbe(mapOf(".env.probe.t" to ".env file", ".env.t" to hygen)),
                suppression = FakeSuppression(),
                foreignPatterns = ForeignPatterns { listOf(ForeignFileType(".env file", listOf(".env.*"))) },
                ourFileTypeName = hygen,
                realFiles = FakeFinder(listOf(".env.t")),
            ),
            suppression = FakeSuppression(),
            reporter = reporter,
            resolver = FileTypeConflictResolver({ _, _ -> false }, {}, { _, _ -> }),
        ).run(listOf("*.t"))

        org.junit.Assert.assertNull(reporter.reported)
    }

    @Test
    fun `a partially claimed-back pair is still reported with only the unclaimed files`() {
        val reporter = RecordingReporter()
        FileTypeConflictController(
            detector = FileTypeConflictDetector(
                probe = FakeProbe(mapOf(".env.probe.t" to ".env file", "app.env.t" to ".env file", ".env.t" to hygen)),
                suppression = FakeSuppression(),
                foreignPatterns = ForeignPatterns { listOf(ForeignFileType(".env file", listOf(".env.*"))) },
                ourFileTypeName = hygen,
                realFiles = FakeFinder(listOf(".env.t", "app.env.t")),
            ),
            suppression = FakeSuppression(),
            reporter = reporter,
            resolver = FileTypeConflictResolver({ _, _ -> false }, {}, { _, _ -> }),
        ).run(listOf("*.t"))

        org.junit.Assert.assertEquals(
            listOf(FileTypeConflict("*.t", ".env file", ".env.probe.t", listOf("app.env.t"))),
            reporter.reported,
        )
    }
}

private class FakeSuppression : ConflictSuppression {
    val suppressed = mutableSetOf<String>()
    override fun isSuppressed(fileTypeName: String): Boolean = fileTypeName in suppressed
    override fun suppress(fileTypeName: String) {
        suppressed += fileTypeName
    }
}
