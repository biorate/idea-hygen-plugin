package com.biorate.hygen.conflicts

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * Seam over the platform file-type manager for the exact-name associations of the claim-back
 * feature (design D2/D4): adds [associateExact] an exact file name (no wildcards) as a file name
 * association of the hygen template type, and removes [removeExact] exactly that association —
 * only from OUR type, never from anyone else's.
 *
 * Production implementation: [PlatformFileTypeManagerSeam]; tests substitute in-memory fakes.
 */
interface ExactFileNameAssociations {

    /**
     * @return `true` only when [name] was added as an exact association of the hygen type; any
     *         missing manager, invalid name or platform failure yields `false`.
     */
    fun associateExact(name: String): Boolean

    /**
     * Removes the exact association of [name] from the hygen type only.
     *
     * @return `true` only when the removal was performed.
     */
    fun removeExact(name: String): Boolean
}

/**
 * Claim store seam for the claim-back flows (tasks §3.1): which exact file names the plugin has
 * claimed. Implemented by the persistent [FileTypeClaimManager]; tests substitute an in-memory
 * fake.
 */
interface FileTypeClaims {

    /**
     * Records [name] as claimed and adds its exact association (best effort).
     *
     * @return `true` when the name was newly claimed.
     */
    fun claim(name: String): Boolean

    /** @return the persisted claimed exact file names (a defensive copy, stable order). */
    fun claimed(): Set<String>

    /**
     * Releases a previously claimed name: forgets it and removes its exact association from the
     * hygen type. Names NOT in the plugin's own persisted state are never touched.
     *
     * @return `true` when a stored claim was released.
     */
    fun release(name: String): Boolean
}

/**
 * Application-level persistent memory of the exact file names the plugin has claimed back
 * (tasks §1.2, design D4): e.g. `.env.t` added as an exact association of the hygen template
 * type so the platform resolves it to hygen ahead of a foreign glob (`.env.*`).
 *
 * Persisted to `hygenFileTypeClaims.xml` next to [HygenFileTypeConflictSettings], in the same
 * style: the component is its own state and loaded state is canonicalized (trimmed, non-blank,
 * wildcard-free, distinct, sorted names only). Only names recorded through [claim] are ever
 * released — exact names added by the user or other plugins are never touched.
 */
@State(name = "HygenFileTypeClaimManager", storages = [Storage("hygenFileTypeClaims.xml")])
@Service(Service.Level.APP)
class FileTypeClaimManager : PersistentStateComponent<FileTypeClaimManager>, FileTypeClaims {

    /**
     * Exact-name association seam; replaceable in tests. Never serialized (transient field).
     */
    @Transient
    internal var associations: ExactFileNameAssociations = PlatformExactFileNameAssociations

    /** Claimed exact file names, e.g. `.env.t`. */
    var claimedFileNames: MutableSet<String> = LinkedHashSet()

    override fun claim(name: String): Boolean {
        val canonical = canonical(name) ?: return false
        if (!claimedFileNames.add(canonical)) return false
        runCatching { associations.associateExact(canonical) }
        return true
    }

    override fun claimed(): Set<String> = LinkedHashSet(claimedFileNames)

    override fun release(name: String): Boolean {
        // Match against OUR stored claims only (case-insensitively, as file-name globs are);
        // an unknown name is a no-op and never removes anyone else's association.
        val stored = claimedFileNames.firstOrNull { it.equals(name.trim(), ignoreCase = true) }
            ?: return false
        if (!claimedFileNames.remove(stored)) return false
        runCatching { associations.removeExact(stored) }
        return true
    }

    override fun getState(): FileTypeClaimManager = this

    override fun loadState(state: FileTypeClaimManager) {
        // Re-validate on load, so whatever version wrote the file the in-memory set is canonical.
        claimedFileNames = canonicalize(state.claimedFileNames)
    }

    companion object {

        fun getInstance(): FileTypeClaimManager =
            ApplicationManager.getApplication().getService(FileTypeClaimManager::class.java)

        /** A usable exact file name: trimmed, non-blank, no wildcards or path separators. */
        internal fun canonical(name: String): String? {
            val trimmed = name.trim()
            return trimmed.takeIf { it.isNotEmpty() && '*' !in it && '/' !in it && '\\' !in it }
        }

        /** Canonical form of [raw]: usable names only, distinct, sorted. */
        internal fun canonicalize(raw: Collection<String>): MutableSet<String> =
            LinkedHashSet(raw.mapNotNull { canonical(it) }.distinct().sorted())
    }
}

/**
 * Production [ExactFileNameAssociations] over the [PlatformFileTypeManagerSeam]; when the file
 * type manager is unavailable, every operation degrades to "not performed" (`false`).
 */
internal object PlatformExactFileNameAssociations : ExactFileNameAssociations {

    override fun associateExact(name: String): Boolean =
        PlatformFileTypeManagerSeam.fromFileTypeManager()?.associateExact(name) ?: false

    override fun removeExact(name: String): Boolean =
        PlatformFileTypeManagerSeam.fromFileTypeManager()?.removeExact(name) ?: false
}
