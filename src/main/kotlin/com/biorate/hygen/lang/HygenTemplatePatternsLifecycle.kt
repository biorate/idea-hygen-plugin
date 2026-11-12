package com.biorate.hygen.lang

import com.biorate.hygen.settings.HygenTemplateSettings
import com.biorate.hygen.settings.HygenTemplateSettingsChangeListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ide.ApplicationInitializedListener
import com.intellij.util.messages.MessageBusConnection

/**
 * Applies the configured hygen template patterns as dynamic [FileTypeManager] associations at IDE
 * startup (design D5) and re-applies them whenever the settings are changed from the configurable.
 *
 * On [applicationInitialized] the sync is deferred via [ApplicationManager.invokeLater] so the
 * file-type manager is fully available; the call is guarded so a missing [FileTypeManager] is a
 * no-op rather than a crash. [HygenPatternAssociator] makes the operation idempotent.
 */
class HygenTemplatePatternsLifecycle : ApplicationInitializedListener {

    private var connection: MessageBusConnection? = null

    override suspend fun execute() {
        // Defer until the application has finished initializing so FileTypeManager is ready.
        ApplicationManager.getApplication().invokeLater { syncFromSettings() }

        // Re-sync whenever the user applies a change in Settings → Tools → Hygen Templates.
        val bus = ApplicationManager.getApplication().messageBus
        connection = bus.connect()
        connection?.subscribe(
            HygenTemplateSettingsChangeListener.TOPIC,
            HygenTemplateSettingsChangeListener { syncFromSettings() },
        )
    }

    private fun syncFromSettings() {
        val gateway = FileTypeManagerAssociationGateway.fromFileTypeManager() ?: return
        val settings = runCatching { HygenTemplateSettings.getInstance() }.getOrNull() ?: return
        runCatching { HygenPatternAssociator(gateway).sync(settings.patterns) }
            // A sync failure must never break startup or the settings apply.
    }
}
