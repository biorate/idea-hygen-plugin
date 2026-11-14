package com.biorate.hygen.lang

import com.biorate.hygen.settings.HygenPatterns

/**
 * Reconciles the [HygenFileType] associations with the configured pattern list (design D1/D5):
 * patterns that should be present but are not are [associated][HygenAssociationGateway.associate],
 * patterns that are present but no longer configured are [removed][HygenAssociationGateway.remove].
 *
 * The diff is computed against what the gateway currently reports, so a full re-sync is exact:
 * stale associations are dropped even when they were added by a previous run or version. It is
 * idempotent — running it twice with no change in configuration performs no further calls, so the
 * IDE's own persistence of dynamic associations never causes duplicate associations.
 *
 * The settings sync owns WILDCARD associations only. Exact-name associations (no `*`, e.g. `.env.t`
 * added by the claim-back flow) belong to the claim lifecycle and are never removed here.
 */
class HygenPatternAssociator(private val gateway: HygenAssociationGateway) {

    /**
     * Syncs the wildcard associations of [HygenFileType] to the canonical form of [patterns]
     * (normalized through [HygenPatterns.normalize], invalid entries dropped, duplicates collapsed).
     * Exact-name associations (no `*`) are left untouched — they belong to the claim lifecycle.
     */
    fun sync(patterns: Collection<String>) {
        val desired = patterns.mapNotNull { HygenPatterns.normalize(it) }.toSet()
        val current = gateway.currentPatterns()
        val toRemove = current.filter { it.contains('*') } - desired
        val toAdd = desired - current
        toRemove.forEach { gateway.remove(it) }
        toAdd.forEach { gateway.associate(it) }
    }
}
