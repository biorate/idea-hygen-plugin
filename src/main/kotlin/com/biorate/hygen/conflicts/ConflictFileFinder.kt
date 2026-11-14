package com.biorate.hygen.conflicts

/**
 * Seam over the project VFS (tasks §2.1, design D1): real file names of files EXISTING in the
 * content roots of all open projects whose names match BOTH [ourPattern] and [foreignPattern].
 * File names only — no content is ever read.
 *
 * Production implementation: [ProjectConflictFileScanner]; tests substitute fixed lists.
 */
fun interface ConflictFileFinder {

    /**
     * @return matching distinct file names in a stable (sorted) order, at most [limit] of them;
     *         empty when the lookup is unavailable or nothing matches.
     */
    fun find(ourPattern: String, foreignPattern: String, limit: Int): List<String>

    /**
     * Matches several (our, foreign) pattern pairs in ONE lookup, so production implementations
     * can walk the project tree a single time for all conflict pairs. The default delegates to
     * [find] per distinct pair (the shape the test fakes already implement).
     *
     * @return the matching names for every distinct [pair]; a pair absent from the result or
     *         mapped to an empty list means no matches (or the lookup is unavailable).
     */
    fun findAll(pairs: Collection<Pair<String, String>>, limit: Int): Map<Pair<String, String>, List<String>> =
        pairs.distinct().associateWith { (ours, foreign) -> find(ours, foreign, limit) }

    companion object {
        /** Cap on the number of real file names offered per conflict pair (design D1). */
        const val MAX_FILES_PER_PAIR = 20
    }
}

/**
 * Seam checking whether a file with exactly [fileName] exists in the content roots of all open
 * projects (claim cleanup, tasks §2.2): a claimed name whose file no longer exists is released.
 * Production implementation: [ProjectConflictFileScanner]; tests substitute lambdas.
 */
fun interface ProjectFileNameLookup {

    /** @return whether the file exists; `false` when the lookup is unavailable. */
    fun exists(fileName: String): Boolean
}
