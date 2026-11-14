package com.biorate.hygen.lang

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Re-runs the conflict detection when a project opens (OpenSpec change
 * `claim-back-conflicting-files`).
 *
 * The startup detection in [HygenTemplatePatternsLifecycle] runs before any project is open, so
 * [com.biorate.hygen.conflicts.ProjectConflictFileScanner] sees no content roots and real-file
 * evidence (and with it the "Claim back" action) is missing from the very first balloon. This
 * activity fires for every project after its startup and re-runs the same lifecycle entry point:
 * the association sync is idempotent, and the balloon reporter dedups identical conflict
 * signatures per session, so repeated runs neither spam nor change anything — but once at least
 * one project is open, the scanner finally sees real files and claim-back is offered.
 *
 * [ProjectActivity] via `<postStartupActivity>` is the modern, non-deprecated project-startup
 * hook (the legacy `StartupActivity` interfaces are deprecated).
 */
class HygenProjectOpenResyncActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val log = Logger.getInstance(HygenProjectOpenResyncActivity::class.java)
        log.info("Hygen diagnostics: project open resync: project=${project.name}")
        runCatching { HygenTemplatePatternsLifecycle.resyncNow() }
        // A resync failure must never break project startup.
    }
}
