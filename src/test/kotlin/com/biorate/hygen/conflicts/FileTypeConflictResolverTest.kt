package com.biorate.hygen.conflicts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [FileTypeConflictResolver] (tasks §2.2): tier 1 (reflective/API removal) and
 * tier 2 (manual instructions fallback) exercised on fakes — no IDE required.
 */
class FileTypeConflictResolverTest {

    private val dotenv = FileTypeConflict("*.t", ".env file", "probe.t")
    private val perl = FileTypeConflict("*.tmpl", "Perl", "probe.tmpl")

    /** Fake remover that succeeds/fails per foreign type name, recording every attempt. */
    private class FakeRemover(
        private val failingTypes: Set<String> = emptySet(),
        private val throwingTypes: Set<String> = emptySet(),
    ) : ShadowingPatternRemover {
        val attempts = mutableListOf<Pair<String, String>>()
        var failAfter: Int? = null

        override fun removePattern(shadowingFileTypeName: String, pattern: String): Boolean {
            attempts += shadowingFileTypeName to pattern
            failAfter?.let { if (attempts.size > it) throw IllegalStateException("platform gone") }
            return shadowingFileTypeName !in failingTypes && shadowingFileTypeName !in throwingTypes
        }
    }

    private class RecordingInstructions : ManualInstructionsShower {
        val shown = mutableListOf<Pair<String, String>>()
        override fun show(shadowingFileTypeName: String, pattern: String) {
            shown += shadowingFileTypeName to pattern
        }
    }

    private class RecordingResync {
        var count = 0
        fun run() {
            count++
        }
    }

    private fun resolver(
        remover: ShadowingPatternRemover,
        resync: RecordingResync,
        instructions: RecordingInstructions,
    ) = FileTypeConflictResolver(remover, { resync.run() }, instructions)

    @Test
    fun `tier1 success removes only the conflicting patterns and resyncs once`() {
        val remover = FakeRemover()
        val resync = RecordingResync()
        val instructions = RecordingInstructions()

        resolver(remover, resync, instructions).resolve(listOf(dotenv, perl))

        assertEquals(
            listOf(".env file" to "*.t", "Perl" to "*.tmpl"),
            remover.attempts,
        )
        assertEquals(1, resync.count)
        assertTrue(instructions.shown.isEmpty())
    }

    @Test
    fun `tier1 failure falls back to tier2 instructions for the failing type only`() {
        val remover = FakeRemover(failingTypes = setOf(".env file"))
        val resync = RecordingResync()
        val instructions = RecordingInstructions()

        resolver(remover, resync, instructions).resolve(listOf(dotenv, perl))

        // The successful tier-1 removal still triggers the re-sync...
        assertEquals(1, resync.count)
        // ...and only the failed conflict gets manual instructions.
        assertEquals(listOf(".env file" to "*.t"), instructions.shown)
    }

    @Test
    fun `all failures show instructions and never resync`() {
        val remover = FakeRemover(failingTypes = setOf(".env file", "Perl"))
        val resync = RecordingResync()
        val instructions = RecordingInstructions()

        resolver(remover, resync, instructions).resolve(listOf(dotenv, perl))

        assertEquals(0, resync.count)
        assertEquals(
            listOf(".env file" to "*.t", "Perl" to "*.tmpl"),
            instructions.shown,
        )
    }

    @Test
    fun `remover throwing an exception is treated as a tier1 failure, not a crash`() {
        val remover = FakeRemover(throwingTypes = setOf(".env file"))
        val resync = RecordingResync()
        val instructions = RecordingInstructions()

        resolver(remover, resync, instructions).resolve(listOf(dotenv))

        assertEquals(0, resync.count)
        assertEquals(listOf(".env file" to "*.t"), instructions.shown)
    }

    @Test
    fun `empty conflict list does nothing`() {
        val remover = FakeRemover()
        val resync = RecordingResync()
        val instructions = RecordingInstructions()

        resolver(remover, resync, instructions).resolve(emptyList())

        assertTrue(remover.attempts.isEmpty())
        assertEquals(0, resync.count)
        assertTrue(instructions.shown.isEmpty())
    }
}

/**
 * Unit tests for [FileTypeConflictController]: the wiring between detection, suppression and
 * reporting (tasks §3.1), on fakes.
 */
class FileTypeConflictControllerTest {

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

    private class RecordingReporter : ConflictReporter {
        var reported: List<FileTypeConflict>? = null
        var suppressCallback: ((String) -> Unit)? = null
        var resolveCallback: ((List<FileTypeConflict>) -> Unit)? = null
        var claimBackCallback: ((List<String>) -> Unit)? = null

        override fun report(
            conflicts: List<FileTypeConflict>,
            onSuppress: (String) -> Unit,
            onResolve: (List<FileTypeConflict>) -> Unit,
            onClaimBack: (List<String>) -> Unit,
        ) {
            reported = conflicts
            suppressCallback = onSuppress
            resolveCallback = onResolve
            claimBackCallback = onClaimBack
        }
    }

    private val noOpResolver = FileTypeConflictResolver(
        remover = { _, _ -> false },
        resync = {},
        instructions = { _, _ -> },
    )

    private fun controller(
        resolution: Map<String, String>,
        suppression: FakeSuppression,
        reporter: RecordingReporter,
    ) = FileTypeConflictController(
        FileTypeConflictDetector(FakeProbe(resolution), suppression, ourFileTypeName = hygen),
        suppression,
        reporter,
        noOpResolver,
    )

    @Test
    fun `conflicts are reported and suppress action reaches the state`() {
        val suppression = FakeSuppression()
        val reporter = RecordingReporter()
        controller(mapOf("probe.t" to ".env file"), suppression, reporter)
            .run(listOf("*.t"))

        assertEquals(listOf(FileTypeConflict("*.t", ".env file", "probe.t")), reporter.reported)
        reporter.suppressCallback!!.invoke(".env file")
        assertTrue(suppression.isSuppressed(".env file"))
    }

    @Test
    fun `suppressed types are not reported again`() {
        val suppression = FakeSuppression().apply { suppress(".env file") }
        val reporter = RecordingReporter()
        controller(mapOf("probe.t" to ".env file"), suppression, reporter)
            .run(listOf("*.t"))

        assertEquals(null, reporter.reported)
    }

    @Test
    fun `no conflicts means no notification`() {
        val reporter = RecordingReporter()
        controller(mapOf("probe.t" to hygen), FakeSuppression(), reporter)
            .run(listOf("*.t"))

        assertEquals(null, reporter.reported)
    }

    @Test
    fun `resolve callback is forwarded to the resolver`() {
        val suppression = FakeSuppression()
        val reporter = RecordingReporter()
        val resync = object {
            var count = 0
        }
        val controller = FileTypeConflictController(
            FileTypeConflictDetector(FakeProbe(mapOf("probe.t" to ".env file")), suppression, ourFileTypeName = hygen),
            suppression,
            reporter,
            FileTypeConflictResolver(
                remover = { _, _ -> true },
                resync = { resync.count++ },
                instructions = { _, _ -> },
            ),
        )
        controller.run(listOf("*.t"))

        // Drive the resolve callback as the balloon action would after explicit confirmation.
        reporter.resolveCallback!!.invoke(reporter.reported!!)

        assertEquals(1, resync.count)
        // Resolution is not suppression: the user asked for a fix, not for silence.
        assertTrue(suppression.suppressed.isEmpty())
    }
}
