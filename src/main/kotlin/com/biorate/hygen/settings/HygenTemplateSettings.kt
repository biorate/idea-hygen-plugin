package com.biorate.hygen.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * Application-level persistent settings for the configurable template filename patterns (design
 * D2). Stores a normalized, distinct, sorted list of wildcard patterns; the default is
 * [HygenPatterns.DEFAULT] (`*.t`).
 *
 * Persisted to `hygenTemplates.xml`. Invalid entries are dropped and the list re-normalized and
 * re-sorted whenever state is loaded from disk, so stored state is always canonical. An empty
 * list is a legitimate state (design D4): it disables extension-based claiming entirely while the
 * `_templates` overrider keeps recognizing files under a `_templates` directory.
 */
@State(name = "HygenTemplateSettings", storages = [Storage("hygenTemplates.xml")])
@Service(Service.Level.APP)
class HygenTemplateSettings : PersistentStateComponent<HygenTemplateSettings> {

    /** Current template patterns in wildcard form, e.g. `*.t`, `*.ejs.t`. */
    var patterns: MutableList<String> = mutableListOf(HygenPatterns.DEFAULT)

    override fun getState(): HygenTemplateSettings = this

    override fun loadState(state: HygenTemplateSettings) {
        // Re-validate on load: drop/normalize invalid entries and keep the list distinct + sorted,
        // so whatever version wrote the file, the in-memory list is canonical.
        patterns = canonicalize(state.patterns)
    }

    companion object {
        fun getInstance(): HygenTemplateSettings =
            ApplicationManager.getApplication().getService(HygenTemplateSettings::class.java)

        /**
         * Normalizes [raw] through [HygenPatterns.normalize], drops invalid entries, removes
         * duplicates and keeps the result sorted. Preserves an empty list (design D4).
         */
        internal fun canonicalize(raw: Collection<String>): MutableList<String> =
            raw.mapNotNull { HygenPatterns.normalize(it) }.distinct().sorted().toMutableList()
    }
}
