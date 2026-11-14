package com.biorate.hygen.conflicts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [FileTypeConflictDetector] (tasks §1.2) against a fake [FileTypeProbe], a fake
 * [ForeignPatterns] and an in-memory [ConflictSuppression] — no IDE required (same seam approach
 * as the association-sync tests).
 */
class FileTypeConflictDetectorTest {

    private val hygen = "Hygen Template"

    /** Fake probe: a map from file name to the file type name the platform would assign. */
    private class FakeProbe(private val resolution: Map<String, String>) : FileTypeProbe {
        override fun getFileTypeByFileName(fileName: String): String? = resolution[fileName]
    }

    /** Fake foreign enumeration: fixed list of foreign types and their patterns. */
    private class FakeForeignPatterns(private val types: List<ForeignFileType>) : ForeignPatterns {
        override fun foreignTypes(): List<ForeignFileType> = types
    }

    private class FakeSuppression(initial: Set<String> = emptySet()) : ConflictSuppression {
        val suppressed = initial.toMutableSet()
        override fun isSuppressed(fileTypeName: String): Boolean = fileTypeName in suppressed
        override fun suppress(fileTypeName: String) {
            suppressed += fileTypeName
        }
    }

    private fun detector(
        resolution: Map<String, String>,
        suppression: FakeSuppression = FakeSuppression(),
        foreign: List<ForeignFileType> = emptyList(),
    ) = FileTypeConflictDetector(
        probe = FakeProbe(resolution),
        suppression = suppression,
        foreignPatterns = FakeForeignPatterns(foreign),
        ourFileTypeName = hygen,
    )

    @Test
    fun `conflict is reported when a foreign type wins the representative name`() {
        // Legacy probe path, e.g. when foreign enumeration is unavailable but the platform still
        // resolves `probe.t` to a foreign type.
        val detector = detector(mapOf("probe.t" to ".env file"))

        assertEquals(
            listOf(FileTypeConflict("*.t", ".env file", "probe.t")),
            detector.detect(listOf("*.t")),
        )
    }

    @Test
    fun `regression - intersection of our pattern and a foreign pattern is detected`() {
        // Real-world shape (user-confirmed in the IDE): the bundled dotenv plugin claims `.env.*`,
        // so a file like `.env.t` matches BOTH our `*.t` and the dotenv type — the platform hands
        // it to dotenv, while a synthetic `probe.t` never reveals this.
        val detector = detector(
            resolution = mapOf(".env.probe.t" to ".env file", "probe.t" to hygen),
            foreign = listOf(ForeignFileType(".env file", listOf(".env.*"))),
        )

        assertEquals(
            listOf(FileTypeConflict("*.t", ".env file", ".env.probe.t")),
            detector.detect(listOf("*.t")),
        )
    }

    @Test
    fun `intersection with a foreign wildcard at both ends is detected`() {
        val detector = detector(
            resolution = mapOf(".env.probe.t" to ".env file"),
            foreign = listOf(ForeignFileType(".env file", listOf("*.env.*"))),
        )

        assertEquals(
            listOf(FileTypeConflict("*.t", ".env file", ".env.probe.t")),
            detector.detect(listOf("*.t")),
        )
    }

    @Test
    fun `no conflict when the platform gives the intersection name to the hygen type`() {
        val detector = detector(
            resolution = mapOf(".env.probe.t" to hygen),
            foreign = listOf(ForeignFileType(".env file", listOf(".env.*"))),
        )

        assertTrue(detector.detect(listOf("*.t")).isEmpty())
    }

    @Test
    fun `patterns without a representable intersection produce no conflict`() {
        // `*.py` and `*.t` cannot match the same file name.
        val detector = detector(
            resolution = mapOf("probe.t.py" to "Python"),
            foreign = listOf(ForeignFileType("Python", listOf("*.py"))),
        )

        assertTrue(detector.detect(listOf("*.t")).isEmpty())
    }

    @Test
    fun `no conflict when the hygen type wins`() {
        val detector = detector(
            resolution = mapOf("probe.t" to hygen, "probe.ejs.t" to hygen),
            foreign = listOf(ForeignFileType(".env file", listOf(".env.*"))),
        )

        assertTrue(detector.detect(listOf("*.t", "*.ejs.t")).isEmpty())
    }

    @Test
    fun `no conflict when the probe resolves to nothing`() {
        val detector = detector(
            resolution = emptyMap(),
            foreign = listOf(ForeignFileType(".env file", listOf(".env.*"))),
        )

        assertTrue(detector.detect(listOf("*.t", "*.tmpl")).isEmpty())
    }

    @Test
    fun `several patterns produce one conflict per shadowed pattern in stable order`() {
        val detector = detector(
            resolution = mapOf(
                ".env.probe.t" to ".env file",
                "probe.tmpl" to "Perl",
                "probe.ejs.t" to hygen,
            ),
            foreign = listOf(
                ForeignFileType(".env file", listOf(".env.*")),
                ForeignFileType("Perl", listOf("*.tmpl")),
            ),
        )

        assertEquals(
            listOf(
                FileTypeConflict("*.t", ".env file", ".env.probe.t"),
                FileTypeConflict("*.tmpl", "Perl", "probe.tmpl"),
            ),
            detector.detect(listOf("*.ejs.t", "*.tmpl", "*.t")),
        )
    }

    @Test
    fun `the same shadowing type found through both probes is reported once`() {
        val detector = detector(
            resolution = mapOf(".env.probe.t" to ".env file", "probe.t" to ".env file"),
            foreign = listOf(ForeignFileType(".env file", listOf(".env.*"))),
        )

        assertEquals(
            // Intersection records win over the legacy own-representative probe.
            listOf(FileTypeConflict("*.t", ".env file", ".env.probe.t")),
            detector.detect(listOf("*.t")),
        )
    }

    @Test
    fun `the hygen type itself is never treated as a foreign shadowing type`() {
        val detector = detector(
            resolution = mapOf(".env.probe.t" to hygen),
            foreign = listOf(ForeignFileType(hygen, listOf("*.t", ".env.*"))),
        )

        assertTrue(detector.detect(listOf("*.t")).isEmpty())
    }

    @Test
    fun `suppressed shadowing types are never reported`() {
        val suppression = FakeSuppression(setOf(".env file"))
        val detector = detector(
            resolution = mapOf("probe.t" to ".env file"),
            suppression = suppression,
            foreign = listOf(ForeignFileType(".env file", listOf(".env.*"))),
        )

        assertTrue(detector.detect(listOf("*.t")).isEmpty())
        // ...while other shadowing types still are.
        val both = detector(
            resolution = mapOf("probe.t" to ".env file", "probe.tmpl" to "Perl"),
            suppression = suppression,
            foreign = listOf(
                ForeignFileType(".env file", listOf(".env.*")),
                ForeignFileType("Perl", listOf("*.tmpl")),
            ),
        )
        assertEquals(
            listOf(FileTypeConflict("*.tmpl", "Perl", "probe.tmpl")),
            both.detect(listOf("*.t", "*.tmpl")),
        )
    }

    @Test
    fun `foreign enumeration failure degrades gracefully to no intersection conflicts`() {
        val detector = FileTypeConflictDetector(
            probe = FakeProbe(emptyMap()),
            suppression = FakeSuppression(),
            foreignPatterns = ForeignPatterns { throw IllegalStateException("registry down") },
            ourFileTypeName = hygen,
        )

        assertTrue(detector.detect(listOf("*.t")).isEmpty())
    }

    @Test
    fun `unavailable foreign enumeration still allows the legacy probe`() {
        val detector = FileTypeConflictDetector(
            probe = FakeProbe(mapOf("probe.t" to ".env file")),
            suppression = FakeSuppression(),
            foreignPatterns = ForeignPatterns { emptyList() },
            ourFileTypeName = hygen,
        )

        assertEquals(
            listOf(FileTypeConflict("*.t", ".env file", "probe.t")),
            detector.detect(listOf("*.t")),
        )
    }

    @Test
    fun `invalid patterns are skipped`() {
        val detector = detector(mapOf("probe.t" to ".env file"))

        assertTrue(detector.detect(listOf("abc", "", "*", "*.t")).let { conflicts ->
            conflicts.map { it.pattern } == listOf("*.t")
        })
    }

    @Test
    fun `duplicate patterns are collapsed`() {
        val detector = detector(mapOf("probe.t" to ".env file"))

        assertEquals(1, detector.detect(listOf("*.t", "t", " *.T ")).size)
    }
}

/**
 * Unit tests for the real-file integration (tasks §2.1/§2.2): the [ConflictFileFinder] seam
 * attached to [FileTypeConflictDetector] — which real files get attached to a conflict, the
 * cap, and the "already claimed back" suppression of a pair.
 */
class FileTypeConflictRealFilesTest {

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

    /** Fake finder with the (our pattern × foreign pattern) → real file names mapping. */
    private class FakeFinder(
        private val files: Map<Pair<String, String>, List<String>>,
        private val throwing: Boolean = false,
    ) : ConflictFileFinder {
        override fun find(ourPattern: String, foreignPattern: String, limit: Int): List<String> {
            if (throwing) throw IllegalStateException("vfs gone")
            return files[ourPattern to foreignPattern].orEmpty()
        }
    }

    private val dotenv = ForeignFileType(".env file", listOf(".env.*"))

    private fun detector(
        resolution: Map<String, String>,
        finder: ConflictFileFinder? = null,
        suppression: FakeSuppression = FakeSuppression(),
    ) = FileTypeConflictDetector(
        probe = FakeProbe(resolution),
        suppression = suppression,
        foreignPatterns = ForeignPatterns { listOf(dotenv) },
        ourFileTypeName = hygen,
        realFiles = finder,
    )

    @Test
    fun `real project files matching both patterns are attached to the conflict`() {
        val detector = detector(
            resolution = mapOf(".env.probe.t" to ".env file", ".env.t" to ".env file", "app.env.t" to ".env file"),
            finder = FakeFinder(mapOf("*.t" to ".env.*" to listOf(".env.t", "app.env.t"))),
        )

        assertEquals(
            listOf(FileTypeConflict("*.t", ".env file", ".env.probe.t", listOf(".env.t", "app.env.t"))),
            detector.detect(listOf("*.t")),
        )
    }

    @Test
    fun `file names are attached distinct and in stable (sorted) order`() {
        val detector = detector(
            resolution = mapOf(".env.probe.t" to ".env file", ".env.t" to ".env file"),
            finder = FakeFinder(mapOf("*.t" to ".env.*" to listOf("b.env.t", "a.env.t", "b.env.t"))),
        )

        assertEquals(
            listOf("a.env.t", "b.env.t"),
            detector.detect(listOf("*.t")).single().conflictingFileNames,
        )
    }

    @Test
    fun `real file names are capped at the per-pair limit`() {
        val many = (1..ConflictFileFinder.MAX_FILES_PER_PAIR + 10).map { "f$it.env.t" }
        val detector = detector(
            resolution = many.associateWith { ".env file" } + (".env.probe.t" to ".env file"),
            finder = FakeFinder(mapOf("*.t" to ".env.*" to many)),
        )

        val conflict = detector.detect(listOf("*.t")).single()
        assertEquals(ConflictFileFinder.MAX_FILES_PER_PAIR, conflict.conflictingFileNames.size)
    }

    @Test
    fun `a pair whose real files ALL already resolve to the hygen type is not reported`() {
        // The real-world claim-back shape: `.env.t` was claimed back via an exact association,
        // so it now resolves to hygen even though the foreign `.env.*` pattern still intersects.
        val detector = detector(
            resolution = mapOf(".env.probe.t" to ".env file", ".env.t" to hygen),
            finder = FakeFinder(mapOf("*.t" to ".env.*" to listOf(".env.t"))),
        )

        assertTrue(detector.detect(listOf("*.t")).isEmpty())
    }

    @Test
    fun `a pair whose real files resolve to several types is still reported`() {
        val detector = detector(
            resolution = mapOf(".env.probe.t" to ".env file", ".env.t" to hygen, "app.env.t" to ".env file"),
            finder = FakeFinder(mapOf("*.t" to ".env.*" to listOf(".env.t", "app.env.t"))),
        )

        // `.env.t` already resolves to the hygen type, so only the still-foreign file is offered.
        assertEquals(
            listOf(FileTypeConflict("*.t", ".env file", ".env.probe.t", listOf("app.env.t"))),
            detector.detect(listOf("*.t")),
        )
    }

    @Test
    fun `no real files found - the conflict is still reported, without claim-back names`() {
        val detector = detector(
            resolution = mapOf(".env.probe.t" to ".env file"),
            finder = FakeFinder(emptyMap()),
        )

        assertEquals(
            listOf(FileTypeConflict("*.t", ".env file", ".env.probe.t")),
            detector.detect(listOf("*.t")),
        )
    }

    @Test
    fun `a failing real-file lookup degrades to no claim-back names, never a crash`() {
        val detector = detector(
            resolution = mapOf(".env.probe.t" to ".env file"),
            finder = FakeFinder(emptyMap(), throwing = true),
        )

        assertEquals(
            listOf(FileTypeConflict("*.t", ".env file", ".env.probe.t")),
            detector.detect(listOf("*.t")),
        )
    }

    @Test
    fun `without the finder the legacy conflicts are unchanged`() {
        val detector = detector(
            resolution = mapOf(".env.probe.t" to ".env file"),
        )

        assertEquals(
            listOf(FileTypeConflict("*.t", ".env file", ".env.probe.t")),
            detector.detect(listOf("*.t")),
        )
    }
}
