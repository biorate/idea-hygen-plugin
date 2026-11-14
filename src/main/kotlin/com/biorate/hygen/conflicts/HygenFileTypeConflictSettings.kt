package com.biorate.hygen.conflicts

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * Application-level persistent suppression memory for file-type conflicts (tasks §2.1): the
 * registry names of foreign file types for which the user chose "Don't show for <type>" and that
 * the detector therefore never reports again.
 *
 * Persisted to `hygenFileTypeConflicts.xml` next to [com.biorate.hygen.settings.HygenTemplateSettings],
 * following the same style: the component is its own state, and state loaded from disk is
 * canonicalized (non-blank, distinct, sorted names only).
 */
@State(name = "HygenFileTypeConflictSettings", storages = [Storage("hygenFileTypeConflicts.xml")])
@Service(Service.Level.APP)
class HygenFileTypeConflictSettings : PersistentStateComponent<HygenFileTypeConflictSettings>, ConflictSuppression {

    /** Foreign file type registry names suppressed by the user, e.g. `.env file`. */
    var suppressedFileTypes: MutableSet<String> = LinkedHashSet()

    override fun isSuppressed(fileTypeName: String): Boolean = fileTypeName in suppressedFileTypes

    override fun suppress(fileTypeName: String) {
        if (fileTypeName.isNotBlank()) suppressedFileTypes += fileTypeName
    }

    override fun getState(): HygenFileTypeConflictSettings = this

    override fun loadState(state: HygenFileTypeConflictSettings) {
        // Re-validate on load, so whatever version wrote the file the in-memory set is canonical.
        suppressedFileTypes = canonicalize(state.suppressedFileTypes)
    }

    companion object {
        fun getInstance(): HygenFileTypeConflictSettings =
            ApplicationManager.getApplication().getService(HygenFileTypeConflictSettings::class.java)

        /** Non-blank, distinct, sorted copy of [raw]. */
        internal fun canonicalize(raw: Collection<String>): MutableSet<String> =
            raw.filter { it.isNotBlank() }.distinct().sorted().toMutableSet()
    }
}
