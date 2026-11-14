package com.biorate.hygen.lang

import com.biorate.hygen.conflicts.BalloonConflictReporter
import com.biorate.hygen.conflicts.FileTypeConflict
import com.biorate.hygen.conflicts.FileTypeConflictController
import com.biorate.hygen.conflicts.FileTypeConflictDetector
import com.biorate.hygen.conflicts.FileTypeConflictResolver
import com.biorate.hygen.conflicts.HygenFileTypeConflictSettings
import com.biorate.hygen.conflicts.ConflictReporter
import com.biorate.hygen.conflicts.NotificationManualInstructionsShower
import com.biorate.hygen.conflicts.PlatformFileTypeManagerSeam
import com.biorate.hygen.settings.HygenTemplateSettings
import com.biorate.hygen.settings.HygenTemplateSettingsChangeListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ide.ApplicationInitializedListener
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.messages.MessageBusConnection

/**
 * Applies the configured hygen template patterns as dynamic [FileTypeManager] associations at IDE
 * startup (design D5) and re-applies them whenever the settings are changed from the configurable.
 *
 * On [applicationInitialized] the sync is deferred via [ApplicationManager.invokeLater] so the
 * file-type manager is fully available; the call is guarded so a missing [FileTypeManager] is a
 * no-op rather than a crash. [HygenPatternAssociator] makes the operation idempotent.
 *
 * After each successful sync the file-type conflict detector runs (tasks §3.1): patterns the
 * platform actually hands to a foreign file type (e.g. the bundled dotenv plugin claiming
 * `.env.t` via `.env.*`) are reported in one aggregated balloon with interactive resolution.
 */
class HygenTemplatePatternsLifecycle : ApplicationInitializedListener {

    private var connection: MessageBusConnection? = null

    private val LOG = Logger.getInstance(HygenTemplatePatternsLifecycle::class.java)

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

    internal fun syncFromSettings() {
        LOG.info("Hygen diagnostics: patterns sync: ${runCatching { HygenTemplateSettings.getInstance().patterns }.getOrElse { "<unavailable: $it>" }}")
        val gateway = FileTypeManagerAssociationGateway.fromFileTypeManager() ?: return
        val settings = runCatching { HygenTemplateSettings.getInstance() }.getOrNull() ?: return
        runCatching { HygenPatternAssociator(gateway).sync(settings.patterns) }
            // A sync failure must never break startup or the settings apply.
        runCatching { detectAndReportConflicts(settings.patterns) }
    }

    /** Runs the conflict detector over [patterns]; failures are silently contained. */
    private fun detectAndReportConflicts(patterns: Collection<String>) {
        LOG.info("Hygen diagnostics: detectAndReportConflicts: patterns=$patterns")
        val seam = PlatformFileTypeManagerSeam.fromFileTypeManager()
        if (seam == null) {
            LOG.warn("Hygen diagnostics: detectAndReportConflicts: no seam (FileTypeManager unavailable)")
            return
        }
        val suppression =
            runCatching { HygenFileTypeConflictSettings.getInstance() }.getOrNull()
        if (suppression == null) {
            LOG.warn("Hygen diagnostics: detectAndReportConflicts: no suppression settings")
            return
        }
        val controller = FileTypeConflictController(
            detector = FileTypeConflictDetector(seam, suppression, foreignPatterns = seam),
            suppression = suppression,
            reporter = LoggingBalloonConflictReporter(BalloonConflictReporter()),
            resolver = FileTypeConflictResolver(
                remover = seam,
                // After a successful tier-1 removal the association sync (and with it the
                // detector) runs again through this same lifecycle entry point, so the fix is
                // verified immediately. The balloon reporter's session dedup keeps this from
                // re-raising the same notification.
                resync = { syncFromSettings() },
                instructions = NotificationManualInstructionsShower(),
            ),
        )
        controller.run(patterns)
    }

    /**
     * Temporary debug wrapper (diagnostic build): logs what the detector produced and what the
     * balloon reporter did with it, then delegates to [BalloonConflictReporter].
     */
    private class LoggingBalloonConflictReporter(
        private val delegate: ConflictReporter,
    ) : ConflictReporter {
        private val log = Logger.getInstance(BalloonConflictReporter::class.java)

        override fun report(
            conflicts: List<FileTypeConflict>,
            onSuppress: (String) -> Unit,
            onResolve: (List<FileTypeConflict>) -> Unit,
        ) {
            if (conflicts.isEmpty()) {
                log.info("Hygen diagnostics: detector result: no conflicts detected")
            } else {
                log.info(
                    "Hygen diagnostics: detector result: ${conflicts.size} conflict(s): " +
                        conflicts.joinToString("; ") { it.pattern + " -> " + it.shadowingFileTypeName },
                )
            }
            delegate.report(conflicts, onSuppress, onResolve)
        }
    }
}
