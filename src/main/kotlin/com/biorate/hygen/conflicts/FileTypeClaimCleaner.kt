package com.biorate.hygen.conflicts

import com.biorate.hygen.lang.HygenFileType
import com.biorate.hygen.settings.HygenPatterns
import com.intellij.openapi.diagnostic.Logger

/**
 * Claim lifecycle cleanup (tasks §2.2, design D4): runs at the start of every sync, before
 * detection. A claimed name is RELEASED when
 *  - its file no longer exists ([existence]), or
 *  - the name no longer matches any of the currently configured (normalized) patterns, or
 *  - its exact association no longer resolves to the hygen type ([probe]).
 *
 * A `null` probe result (lookup unavailable) keeps the claim — cleanup must never release a
 * claim just because a lookup failed. All failures degrade to "keep the claim", never throw.
 *
 * @return the names actually released.
 */
class FileTypeClaimCleaner(
    private val claims: FileTypeClaims,
    private val probe: FileTypeProbe,
    private val ourFileTypeName: String = HygenFileType.name,
) {
    private val log = Logger.getInstance(FileTypeClaimCleaner::class.java)

    fun cleanup(
        patterns: Collection<String>,
        existence: (String) -> Boolean = { true },
    ): List<String> {
        val normalized = patterns.mapNotNull { HygenPatterns.normalize(it) }
        val released = mutableListOf<String>()
        for (name in claims.claimed()) {
            val probe = runCatching { probe.getFileTypeByFileName(name) }.getOrNull()
            val keep = existence(name) &&
                normalized.any { GlobMatcher.matches(it, name) } &&
                (probe == null || probe == ourFileTypeName)
            if (keep) continue
            val releasedNow = runCatching { claims.release(name) }.getOrDefault(false)
            if (releasedNow) {
                released += name
                if (normalized.any { GlobMatcher.matches(it, name) } && existence(name)) {
                    log.warn(
                        "claim probe mismatch: releasing claim for \"$name\"; " +
                            "probe returned ${probe ?: "null"} (expected \"$ourFileTypeName\")",
                    )
                }
            }
        }
        return released
    }
}
