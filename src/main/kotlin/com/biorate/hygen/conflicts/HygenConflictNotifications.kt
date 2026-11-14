package com.biorate.hygen.conflicts

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger

/**
 * IDE implementations of the notification seams. Both go through the `Hygen File Type Conflicts`
 * notification group registered in `plugin.xml`; both are safe no-ops when the IDE UI is not
 * available (early startup, headless context).
 */
object HygenConflictNotifications {

    /** Must match the `<notificationGroup id>` in `plugin.xml`. */
    const val GROUP_ID = "Hygen File Type Conflicts"

    internal fun notifyNow(block: () -> Unit) {
        val application = ApplicationManager.getApplication() ?: return
        // The conflict hook runs inside the association sync, which may be invoked off the EDT.
        application.invokeLater {
            runCatching(block)
        }
    }

    internal fun groupIsAvailable(): Boolean = runCatching {
        NotificationGroupManager.getInstance().getNotificationGroup(GROUP_ID)
    }.isSuccess
}

/**
 * Production [ConflictReporter] (tasks §2.1): one aggregated balloon listing every unsuppressed
 * conflict, with a "Don't show for <type>" action per foreign type and a single "Resolve…"
 * action whose activation is the explicit user confirmation tier 1 requires (design D2).
 *
 * The same conflict set is only reported once per session, so the recurring association sync
 * does not re-raise a balloon the user has already seen or dismissed; a change in the conflict
 * set (e.g. a newly shadowed pattern) is reported again.
 */
class BalloonConflictReporter : ConflictReporter {

    private val LOG = Logger.getInstance(BalloonConflictReporter::class.java)

    @Volatile
    private var lastReportedSignature: String? = null

    override fun report(
        conflicts: List<FileTypeConflict>,
        onSuppress: (String) -> Unit,
        onResolve: (List<FileTypeConflict>) -> Unit,
    ) {
        if (conflicts.isEmpty()) {
            LOG.info("Hygen diagnostics: BalloonConflictReporter.report: called with empty conflicts, early return")
            return
        }
        LOG.info(
            "Hygen diagnostics: BalloonConflictReporter.report: called with ${conflicts.size} conflict(s): " +
                conflicts.joinToString("; ") { it.pattern + " -> " + it.shadowingFileTypeName },
        )
        val signature = conflicts.joinToString("|") { "${it.shadowingFileTypeName}:${it.pattern}" }
        if (signature == lastReportedSignature) {
            LOG.info("Hygen diagnostics: BalloonConflictReporter.report: dedup suppressed (signature match)")
            return
        }
        lastReportedSignature = signature

        HygenConflictNotifications.notifyNow {
            if (!HygenConflictNotifications.groupIsAvailable()) {
                LOG.warn("Hygen diagnostics: BalloonConflictReporter: notification group not available, balloon skipped")
                return@notifyNow
            }
            val listing = conflicts.joinToString("<br/>") { conflict ->
                "&nbsp;&nbsp;• <code>${conflict.pattern}</code> " +
                    "(e.g. <code>${conflict.representativeFileName}</code>) " +
                    "is claimed by <b>${conflict.shadowingFileTypeName}</b>"
            }
            val notification = NotificationGroupManager.getInstance()
                .getNotificationGroup(HygenConflictNotifications.GROUP_ID)
                .createNotification(
                    "Hygen templates: conflicting file types",
                    "Some template patterns are shadowed by other file types, so those files lose " +
                        "hygen highlighting and inspections:<br/>$listing",
                    NotificationType.WARNING,
                )
            conflicts.map { it.shadowingFileTypeName }.distinct().forEach { fileTypeName ->
                notification.addAction(
                    NotificationAction.createSimpleExpiring("Don't show for $fileTypeName") {
                        onSuppress(fileTypeName)
                    },
                )
            }
            notification.addAction(
                NotificationAction.createSimpleExpiring("Resolve…") { onResolve(conflicts) },
            )
            notification.notify(null)
            LOG.info("Hygen diagnostics: BalloonConflictReporter.report: notification.notify() called")
        }
    }
}

/**
 * Production [ManualInstructionsShower] — tier 2 (tasks §2.2): an informational balloon with the
 * exact manual steps that remove the conflicting pattern.
 */
class NotificationManualInstructionsShower : ManualInstructionsShower {

    override fun show(shadowingFileTypeName: String, pattern: String) {
        HygenConflictNotifications.notifyNow {
            if (!HygenConflictNotifications.groupIsAvailable()) return@notifyNow
            NotificationGroupManager.getInstance()
                .getNotificationGroup(HygenConflictNotifications.GROUP_ID)
                .createNotification(
                    "Hygen templates: manual resolution required",
                    "The pattern <code>$pattern</code> could not be removed from " +
                        "<b>$shadowingFileTypeName</b> automatically. Remove it manually: " +
                        "<b>Settings → Editor → File Types → $shadowingFileTypeName</b>, " +
                        "select <code>$pattern</code> in the patterns list and press «−».",
                    NotificationType.INFORMATION,
                )
                .notify(null)
        }
    }
}
