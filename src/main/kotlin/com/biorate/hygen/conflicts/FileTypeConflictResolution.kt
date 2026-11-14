package com.biorate.hygen.conflicts

import com.intellij.openapi.diagnostic.Logger

/**
 * Seam over the IDE notification UI (tasks §2.1): shows one aggregated balloon listing all
 * [conflicts]. The callbacks are the user's explicit choices from the balloon:
 *  - [onSuppress] — "Don't show for <type>" was activated for a foreign file type name;
 *  - [onResolve] — the user explicitly confirmed a resolution attempt for the given conflicts
 *    (tier 1 never runs without this confirmation);
 *  - [onClaimBack] — the user explicitly confirmed claiming back the given exact file names
 *    (tasks §3.1; never runs without the confirmation-dialog confirmation).
 *
 * Implementations must never invoke any callback on their own.
 */
fun interface ConflictReporter {

    fun report(
        conflicts: List<FileTypeConflict>,
        onSuppress: (String) -> Unit,
        onResolve: (List<FileTypeConflict>) -> Unit,
        onClaimBack: (List<String>) -> Unit,
    )
}

/**
 * Seam over the claim-back confirmation UI (tasks §3.1): shows the [fileNames] with checkboxes
 * for the user to pick from.
 *
 * @return the names the user confirmed, or `null` when the dialog was cancelled or the UI is
 *         unavailable — never an implicit "everything".
 */
fun interface ClaimSelectionShower {

    fun show(fileNames: List<String>): List<String>?
}

/**
 * Claim-back flow (tasks §3.1, design D5): claims the user-confirmed exact file names through
 * the [FileTypeClaims] store and, when at least one was newly claimed, re-runs the association
 * sync through the existing lifecycle so the fix takes effect immediately.
 */
class FileTypeClaimBacker(
    private val claims: FileTypeClaims,
    private val resync: () -> Unit,
) {

    fun claimBack(fileNames: Collection<String>) {
        if (fileNames.isEmpty()) return
        var claimedAny = false
        val claimed = mutableListOf<String>()
        for (name in fileNames) {
            val added = runCatching { claims.claim(name) }.getOrDefault(false)
            if (added) claimed += name
            claimedAny = added || claimedAny
        }
        LOG.info(
            "Hygen diagnostics: claim-back done: claimed ${claimed.size} name(s) $claimed; " +
                "resync scheduled=$claimedAny",
        )
        if (claimedAny) {
            runCatching { resync() }
        }
    }

    private companion object {
        private val LOG = Logger.getInstance(FileTypeClaimBacker::class.java)
    }
}

/**
 * Seam for the tier-2 fallback (tasks §2.2): when the shadowing pattern cannot be removed
 * programmatically, the user gets step-by-step manual instructions instead.
 */
fun interface ManualInstructionsShower {

    fun show(shadowingFileTypeName: String, pattern: String)
}

/**
 * Seam over the platform file-type manager for the tier-1 removal (tasks §2.2).
 *
 * Production implementation ([PlatformFileTypeManagerSeam]) removes exactly the one conflicting
 * pattern from the foreign type — nothing else — and reports failure (never throws) so the caller
 * can fall back to tier 2.
 */
fun interface ShadowingPatternRemover {

    /**
     * Removes [pattern] from the foreign file type named [shadowingFileTypeName].
     *
     * @return `true` only when the removal was performed; any missing type, missing API or
     *         platform failure yields `false`.
     */
    fun removePattern(shadowingFileTypeName: String, pattern: String): Boolean
}

/**
 * Resolution of confirmed conflicts (tasks §2.2), tier 1 with graceful tier-2 degradation:
 * every conflict is a removal attempt; the ones that fail fall back to manual instructions.
 * When at least one removal succeeded, [resync] re-runs the association sync (and thereby the
 * detector) through the existing lifecycle so the fix takes effect immediately.
 */
class FileTypeConflictResolver(
    private val remover: ShadowingPatternRemover,
    private val resync: () -> Unit,
    private val instructions: ManualInstructionsShower,
) {

    fun resolve(conflicts: Collection<FileTypeConflict>) {
        if (conflicts.isEmpty()) return
        var removedAny = false
        val manual = mutableListOf<FileTypeConflict>()
        for (conflict in conflicts) {
            val removed = runCatching { remover.removePattern(conflict.shadowingFileTypeName, conflict.pattern) }
                .getOrDefault(false)
            if (removed) removedAny = true else manual += conflict
        }
        if (removedAny) {
            runCatching { resync() }
        }
        manual.forEach { conflict ->
            runCatching { instructions.show(conflict.shadowingFileTypeName, conflict.pattern) }
        }
    }
}

/**
 * Wires detection into reporting (tasks §3.1): runs the detector over the applied patterns and,
 * when unsuppressed conflicts exist, offers one aggregated notification whose actions carry the
 * user's choices back into suppression and resolution.
 */
class FileTypeConflictController(
    private val detector: FileTypeConflictDetector,
    private val suppression: ConflictSuppression,
    private val reporter: ConflictReporter,
    private val resolver: FileTypeConflictResolver,
    private val claimBacker: FileTypeClaimBacker? = null,
) {

    fun run(patterns: Collection<String>) {
        val conflicts = runCatching { detector.detect(patterns) }.getOrNull().orEmpty()
        if (conflicts.isEmpty()) return
        reporter.report(
            conflicts,
            onSuppress = { fileTypeName -> runCatching { suppression.suppress(fileTypeName) } },
            onResolve = { confirmed -> runCatching { resolver.resolve(confirmed) } },
            onClaimBack = { names -> runCatching { claimBacker?.claimBack(names) } },
        )
    }
}
