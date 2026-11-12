package com.biorate.hygen.settings

import com.intellij.util.messages.Topic

/**
 * Application-wide broadcast fired whenever the stored template pattern list is applied from the
 * Settings → Tools → Hygen Templates page. The association sync (design D1/D5) listens to this to
 * re-apply [FileTypeManager] associations without an IDE restart.
 */
fun interface HygenTemplateSettingsChangeListener {
    fun patternsChanged()

    companion object {
        @JvmField
        val TOPIC: Topic<HygenTemplateSettingsChangeListener> = Topic.create(
            "hygen template patterns changed",
            HygenTemplateSettingsChangeListener::class.java,
        )
    }
}
