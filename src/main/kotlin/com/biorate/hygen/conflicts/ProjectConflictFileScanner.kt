package com.biorate.hygen.conflicts

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile

/**
 * Name-only view over one file-system node (tasks §2.1/§2.2). The production adapter wraps a
 * [VirtualFile]; tests substitute pure in-memory fakes, so the walk itself is unit-testable
 * without the IDE.
 */
internal interface ScanNode {
    val name: String
    val isDirectory: Boolean
    val children: List<ScanNode>
}

/**
 * The pure, name-only depth-first walk over the content roots of ALL open projects. Prunes
 * [skippedDirectoryNames] directories by name (case-insensitive), counts every visited node in
 * [WalkStats.files] and stops the whole walk when [onFile] returns `false`.
 *
 * Kept free of any IntelliJ API so it can be tested with pure fakes (pruning, multi-project
 * root collection, nested matches).
 */
internal object ConflictFileWalker {

    /** Counters collected during one scan, for diagnostics. */
    class Stats {
        var projects = 0
        var roots = 0
        var files = 0
        var capped = false
    }

    /**
     * Walks one root list per project; @return `false` when [onFile] asked to stop the walk.
     */
    fun walk(
        projectRoots: List<List<ScanNode>>,
        skippedDirectoryNames: Set<String>,
        maxNodes: Int,
        onFile: (String) -> Boolean,
    ): Stats {
        val stats = Stats()
        for (roots in projectRoots) {
            stats.projects++
            stats.roots += roots.size
            for (root in roots) {
                if (!walkTree(root, stats, skippedDirectoryNames, maxNodes, onFile)) return stats
            }
        }
        return stats
    }

    /** @return `false` when [onFile] asked to stop the whole walk. */
    private fun walkTree(
        root: ScanNode,
        stats: Stats,
        skippedDirectoryNames: Set<String>,
        maxNodes: Int,
        onFile: (String) -> Boolean,
    ): Boolean {
        val stack = ArrayDeque<ScanNode>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            if (++stats.files > maxNodes) {
                stats.capped = true
                return false
            }
            val file = stack.removeLast()
            for (child in file.children) {
                if (child.isDirectory) {
                    if (child.name.lowercase() in skippedDirectoryNames) continue
                    stack.addLast(child)
                } else if (!onFile(child.name)) {
                    return false
                }
            }
        }
        return true
    }
}

/**
 * Production [ConflictFileFinder] and [ProjectFileNameLookup] (tasks §2.1/§2.2, design D1):
 * name-only walks over the content roots of all open projects, comparing file names with
 * [GlobMatcher] — no content reads, no recursive descent beyond directory names.
 *
 * All conflict pairs are matched in a SINGLE walk ([findAll]): one visitor evaluates every
 * pair per file name, so N pairs cost one tree traversal, not N. Heavy, dependency-only
 * directories ([SKIPPED_DIRECTORY_NAMES]) are pruned by name (case-insensitive), which keeps
 * the real corpus small even in projects with huge `node_modules`/`build` trees.
 *
 * Robust against early-startup timing: the project-open resync can fire before the VFS has
 * refreshed the project trees, which used to surface as `contentRoots=1, filesVisited=1`
 * (module roots not yet committed + a root whose children were not loaded). Root collection
 * therefore falls back to per-module content roots and, as a last resort, to the project base
 * dir resolved through a synchronous VFS refresh — and any content root that still reports no
 * children gets one recursive refresh before the walk, so the scanner sees real files no matter
 * WHICH project triggered the resync.
 *
 * Bounded by design: [findAll] stops collecting names for a pair after [limit] matches (and
 * stops the walk entirely when every pair is saturated), [exists] stops at the first match,
 * and every walk caps the total number of visited nodes so pathological trees cannot stall
 * the sync. Every entry point is guarded: a missing project manager or VFS failure is an
 * empty/no answer, never a crash.
 */
class ProjectConflictFileScanner : ConflictFileFinder, ProjectFileNameLookup {

    override fun find(ourPattern: String, foreignPattern: String, limit: Int): List<String> {
        if (limit <= 0) return emptyList()
        return findAll(listOf(ourPattern to foreignPattern), limit)
            .withDefault { emptyList() }
            .getValue(ourPattern to foreignPattern)
    }

    override fun findAll(pairs: Collection<Pair<String, String>>, limit: Int): Map<Pair<String, String>, List<String>> {
        val distinct = pairs.distinct()
        if (limit <= 0 || distinct.isEmpty()) return distinct.associateWith { emptyList() }
        val found = LinkedHashMap<Pair<String, String>, LinkedHashSet<String>>(distinct.size)
        for (pair in distinct) found[pair] = LinkedHashSet()
        val stats = walk { name ->
            for ((pair, names) in found) {
                if (names.size < limit &&
                    GlobMatcher.matches(pair.first, name) &&
                    GlobMatcher.matches(pair.second, name)
                ) {
                    names += name
                }
            }
            // Continue until every pair has collected its quota; stop (false) only then.
            found.values.any { it.size < limit }
        }
        LOG.info(
            "Hygen diagnostics: project file scan (find): projects=${stats.projects}, " +
                "contentRoots=${stats.roots}, filesVisited=${stats.files}, pairs=${found.size}, " +
                "matches=${found.values.sumOf { it.size }} (capped=${stats.capped})",
        )
        // Sort per pair for a stable offer order independent of the VFS walk order.
        return found.mapValues { it.value.toList().sorted() }
    }

    override fun exists(fileName: String): Boolean {
        var found = false
        val stats = walk { name ->
            if (name.equals(fileName, ignoreCase = true)) {
                found = true
                false // stop the walk
            } else {
                true
            }
        }
        LOG.info(
            "Hygen diagnostics: project file scan (exists): projects=${stats.projects}, " +
                "contentRoots=${stats.roots}, filesVisited=${stats.files}, " +
                "match=$found for [$fileName] (capped=${stats.capped})",
        )
        return found
    }

    /**
     * Runs [onFile] over every regular file under the content roots of ALL open projects,
     * inside a read action. Returns the walk counters; an empty result (`projects=0`) means the
     * environment (no application, no project manager, or no open project) made a real-file
     * scan impossible.
     */
    private fun walk(onFile: (String) -> Boolean): ConflictFileWalker.Stats {
        val empty = ConflictFileWalker.Stats()
        val application = ApplicationManager.getApplication() ?: return empty
        val projects = runCatching {
            val manager = ProjectManager.getInstanceIfCreated() ?: return empty
            manager.openProjects
        }.getOrNull() ?: return empty
        if (projects.isEmpty()) return empty
        // Refresh-before-walk must happen OUTSIDE the read action: a synchronous VFS refresh
        // from within a read action is rejected by the platform.
        val projectRoots = projects.map { project -> contentRootCandidates(project) }
        return com.intellij.openapi.application.ReadAction.compute<ConflictFileWalker.Stats, RuntimeException> {
            ConflictFileWalker.walk(
                projectRoots.map { roots -> roots.map(::VirtualFileScanNode) },
                SKIPPED_DIRECTORY_NAMES,
                MAX_VISITED_NODES,
                onFile,
            )
        }
    }

    /**
     * All content-root candidates of one project, deduplicated by URL: the project-wide content
     * roots, then per-module content roots (committed later at startup), then the project base
     * dir resolved through a synchronous VFS refresh as a last resort — so the scan never ends
     * up tied to whichever single project happened to have its roots committed first.
     */
    private fun contentRootCandidates(project: Project): List<VirtualFile> {
        val roots = LinkedHashMap<String, VirtualFile>()
        runCatching { ProjectRootManager.getInstance(project).contentRoots }.getOrNull().orEmpty()
            .forEach { roots.putIfAbsent(runCatching { it.url }.getOrDefault(it.name), it) }
        runCatching { ModuleManager.getInstance(project).modules }.getOrNull().orEmpty()
            .forEach { module ->
                runCatching { ModuleRootManager.getInstance(module).contentRoots }.getOrNull().orEmpty()
                    .forEach { roots.putIfAbsent(runCatching { it.url }.getOrDefault(it.name), it) }
            }
        if (roots.isEmpty()) {
            val basePath = runCatching { project.basePath }.getOrNull()
            if (!basePath.isNullOrBlank()) {
                runCatching {
                    LocalFileSystem.getInstance().refreshAndFindFileByPath(basePath)
                }.getOrNull()?.let { roots.putIfAbsent(runCatching { it.url }.getOrDefault(it.name), it) }
            }
        }
        // A root whose children are not loaded yet (VFS not refreshed at project-open time) is
        // refreshed recursively here, outside the walk's read action.
        for (root in roots.values) {
            val children = runCatching { root.children }.getOrNull().orEmpty()
            if (children.isEmpty()) {
                runCatching { root.refresh(false, true) }
            }
        }
        return roots.values.toList()
    }

    /** [ScanNode] adapter over a platform [VirtualFile]; all access failures degrade to empty. */
    private class VirtualFileScanNode(private val file: VirtualFile) : ScanNode {
        override val name: String get() = file.name
        override val isDirectory: Boolean get() = file.isDirectory
        override val children: List<ScanNode>
            get() = runCatching { file.children }.getOrNull().orEmpty().map(::VirtualFileScanNode)
    }

    companion object {
        private val LOG = Logger.getInstance(ProjectConflictFileScanner::class.java)

        /** Hard bound on visited nodes for the whole single pass (defence against pathological trees). */
        private const val MAX_VISITED_NODES = 50_000

        /**
         * Directory names (compared case-insensitively) that never contain template corpus files:
         * dependency stores, build outputs and IDE metadata. Pruning them by name keeps the walk
         * proportional to the real project sources instead of the full dependency tree.
         */
        internal val SKIPPED_DIRECTORY_NAMES = setOf(
            "node_modules",
            ".git",
            "build",
            "dist",
            "target",
            "out",
            ".gradle",
            ".idea",
            ".intellijplatform",
            "vendor",
            "pnpm-store",
        )
    }
}
