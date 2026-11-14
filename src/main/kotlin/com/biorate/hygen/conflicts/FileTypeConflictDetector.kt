package com.biorate.hygen.conflicts

import com.biorate.hygen.lang.HygenFileType
import com.biorate.hygen.settings.HygenPatterns

/**
 * Read-only seam over the platform [com.intellij.openapi.fileTypes.FileTypeManager] lookup used by
 * the detector, so conflict detection is unit-testable without the IDE (same approach as the
 * association-sync tests and their [com.biorate.hygen.lang.HygenAssociationGateway] fake).
 *
 * The seam returns the resolved file type's registry name rather than the [FileType] instance:
 * the detector only ever compares names, and returning a name keeps fakes trivial.
 */
fun interface FileTypeProbe {

    /**
     * @return the registry name of the file type the platform currently assigns to [fileName],
     *         or `null` when the lookup is unavailable in the current context.
     */
    fun getFileTypeByFileName(fileName: String): String?
}

/**
 * One foreign file type as enumerated by the [ForeignPatterns] seam: its registry [name] and the
 * file-name patterns (presentable form, `*` wildcards allowed anywhere) currently associated
 * with it.
 */
data class ForeignFileType(
    val name: String,
    val patterns: List<String>,
)

/**
 * Read-only seam enumerating the FOREIGN file types registered with the platform and their
 * patterns, so the detector can probe the pattern INTERSECTION between our associations and
 * everyone else's. Backed in production by `FileTypeManager.registeredFileTypes` +
 * `getAssociations(FileType)`; tests substitute fixed lists.
 */
fun interface ForeignPatterns {

    /**
     * @return every registered foreign file type (our own hygen type excluded) with at least one
     *         known pattern, or an empty list when enumeration is unavailable — detection then
     *         degrades to probing our own representative names only, and never throws.
     */
    fun foreignTypes(): List<ForeignFileType>
}

/**
 * Suppression memory seam (tasks §2.1): which foreign file types the user asked not to be
 * bothered about again. Implemented by the persistent
 * [HygenFileTypeConflictSettings]; tests substitute an in-memory set.
 */
interface ConflictSuppression {

    fun isSuppressed(fileTypeName: String): Boolean

    fun suppress(fileTypeName: String)
}

/**
 * Detects (tasks §1.2) which of the applied template patterns are shadowed by a foreign file
 * type, via two complementary probes:
 *
 * 1. **Intersection probing** — for each of our normalized patterns × each foreign type's pattern
 *    (through the [ForeignPatterns] seam), a representative file name lying in the intersection
 *    of both patterns is built (pure [intersectionRepresentative], e.g. our `*.t` × foreign
 *    `.env.*` → `.env.probe.t`) and resolved through the platform's `getFileTypeByFileName()` —
 *    the exact lookup the IDE performs for any opened file. This catches the real-world case a
 *    synthetic `probe.t` never sees: the bundled dotenv plugin claiming `.env.t` via `.env.*`.
 * 2. **Own-representative probing** — the legacy `representativeName(pattern)` probe, kept as a
 *    fallback for foreign shadowing that the enumeration seam could not see.
 *
 * Detection makes no assumptions about association order or registration: whatever the platform
 * would actually hand the file to is what gets reported. Suppressed foreign types (tasks §2.1)
 * are never reported, and each (pattern, shadowing type) pair is reported once.
 */
class FileTypeConflictDetector(
    private val probe: FileTypeProbe,
    private val suppression: ConflictSuppression,
    private val foreignPatterns: ForeignPatterns = ForeignPatterns { emptyList() },
    private val ourFileTypeName: String = HygenFileType.name,
) {

    /**
     * @return the conflicts for [patterns] (normalized, deduplicated, in a stable order), empty
     *         when every pattern resolves to the hygen type, is invalid, or its winning file
     *         type is suppressed.
     */
    fun detect(patterns: Collection<String>): List<FileTypeConflict> {
        val normalized = patterns.mapNotNull { HygenPatterns.normalize(it) }.distinct().sorted()
        val conflicts = linkedMapOf<Pair<String, String>, FileTypeConflict>()
        for (pattern in normalized) {
            addConflicts(conflicts, pattern, intersectionConflicts(pattern))
            addConflicts(conflicts, pattern, ourRepresentativeConflicts(pattern))
        }
        return conflicts.values.toList()
    }

    /** Legacy probe: resolve our own representative name for [pattern]. */
    private fun ourRepresentativeConflicts(pattern: String): List<FileTypeConflict> {
        val representative = representativeName(pattern) ?: return emptyList()
        return conflictIfForeign(pattern, representative)
    }

    /** Intersection probe: our [pattern] × every enumerated foreign pattern. */
    private fun intersectionConflicts(pattern: String): List<FileTypeConflict> {
        val foreignTypes = runCatching { foreignPatterns.foreignTypes() }.getOrDefault(emptyList())
        val conflicts = mutableListOf<FileTypeConflict>()
        for (foreign in foreignTypes) {
            if (foreign.name == ourFileTypeName) continue
            for (foreignPattern in foreign.patterns) {
                val representative =
                    intersectionRepresentative(pattern, foreignPattern) ?: continue
                conflicts += conflictIfForeign(pattern, representative)
            }
        }
        return conflicts
    }

    /**
     * Resolves [representative] through the platform and turns it into a conflict when a foreign,
     * non-suppressed file type wins it; a `null` resolution (lookup unavailable) or a resolution
     * to our own type means no conflict.
     */
    private fun conflictIfForeign(pattern: String, representative: String): List<FileTypeConflict> {
        val winner = probe.getFileTypeByFileName(representative) ?: return emptyList()
        if (winner == ourFileTypeName || suppression.isSuppressed(winner)) return emptyList()
        return listOf(FileTypeConflict(pattern, winner, representative))
    }

    private fun addConflicts(
        into: LinkedHashMap<Pair<String, String>, FileTypeConflict>,
        pattern: String,
        found: List<FileTypeConflict>,
    ) {
        for (conflict in found) {
            into.putIfAbsent(conflict.pattern to conflict.shadowingFileTypeName, conflict)
        }
    }
}
