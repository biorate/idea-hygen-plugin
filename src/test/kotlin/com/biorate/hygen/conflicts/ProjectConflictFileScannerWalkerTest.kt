package com.biorate.hygen.conflicts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ConflictFileWalker] (the pure name-only walk) with pure in-memory fakes —
 * no IDE required. Covers the regression from the live-log failure `projects=3,
 * contentRoots=1, filesVisited=1`: the walk must visit every open project's roots, descend
 * into nested directories, prune heavy dependency directories by name and stop on demand.
 */
class ProjectConflictFileScannerWalkerTest {

    /** Pure fake file-system node. */
    private class Node(
        override val name: String,
        override val isDirectory: Boolean = false,
        private val childNodes: List<Node> = emptyList(),
    ) : ScanNode {
        override val children: List<ScanNode> = childNodes
    }

    private fun dir(name: String, vararg children: Node) = Node(name, isDirectory = true, childNodes = children.toList())
    private fun file(name: String) = Node(name)

    private val skipped = ProjectConflictFileScanner.SKIPPED_DIRECTORY_NAMES

    private fun walk(vararg projects: List<Node>, onFile: (String) -> Boolean = { true }) =
        ConflictFileWalker.walk(projects.toList(), skipped, maxNodes = 50_000, onFile = onFile)

    @Test
    fun `walk visits files of every open project, not just the resyncing one`() {
        // Regression for the live log: three open projects, but only one project's root was
        // walked (filesVisited=1). The walker must consume one root list per project.
        val stats = walk(
            listOf(dir("core", file("README.md"))),
            listOf(dir("application-template", file(".env.t"))),
            listOf(dir("other", file("x.t"))),
        )
        assertEquals(3, stats.projects)
        assertEquals(3, stats.roots)
    }

    @Test
    fun `walk finds a nested match under a template directory`() {
        // Real-world shape: application-template contains _templates/**/.env.t under its
        // content root — a deep, nested directory chain must be descended into.
        val visited = mutableListOf<String>()
        walk(
            listOf(
                dir(
                    "application-template",
                    dir("src", file("Main.kt")),
                    dir("_templates", dir("component", dir("new", file(".env.t"), file("index.ejs.t")))),
                ),
            ),
        ) { visited += it; true }

        assertTrue(".env.t must be visited" , ".env.t" in visited)
        assertTrue("index.ejs.t must be visited", "index.ejs.t" in visited)
    }

    @Test
    fun `heavy dependency directories are pruned by name`() {
        val visited = mutableListOf<String>()
        walk(
            listOf(
                dir(
                    "root",
                    dir("node_modules", file("should-not-appear.t")),
                    dir("NODE_MODULES", file("also-not.t")),
                    dir(".git", file("hook.t")),
                    dir("Build", file("generated.t")),
                    dir("src", file("real.t")),
                ),
            ),
        ) { visited += it; true }

        assertEquals(listOf("real.t"), visited)
    }

    @Test
    fun `walk stops when onFile returns false and reports no cap`() {
        val visited = mutableListOf<String>()
        val stats = walk(
            listOf(dir("root", file("a.t"), file("b.t"))),
            listOf(dir("other", file("c.t"))),
        ) { visited += it; false }

        assertEquals(listOf("a.t"), visited)
        assertFalse(stats.capped)
        // The second project is never entered after the stop.
        assertEquals(1, stats.projects)
    }

    @Test
    fun `walk reports capped after the node budget is exhausted`() {
        val big = List(100) { idx -> dir("p$idx", file("f$idx.t")) }
        val stats = ConflictFileWalker.walk(listOf(listOf(dir("root", *big.toTypedArray()))), skipped, maxNodes = 10) { true }
        assertTrue(stats.capped)
        assertTrue(stats.files <= 11)
    }

    @Test
    fun `empty project root lists are tolerated`() {
        val visited = mutableListOf<String>()
        val stats = walk(emptyList(), listOf(dir("root", file("a.t")))) { visited += it; true }
        assertEquals(2, stats.projects)
        assertEquals(listOf("a.t"), visited)
    }
}
