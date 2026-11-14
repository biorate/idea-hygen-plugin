package com.biorate.hygen.conflicts

import com.biorate.hygen.settings.HygenPatterns

/**
 * A single detected conflict: one of the plugin's applied template [pattern]s resolves to a
 * foreign [shadowingFileTypeName] instead of the hygen file type, so files such as
 * [representativeFileName] lose hygen highlighting and inspections.
 *
 * [shadowingFileTypeName] is the display/registry name of the winning file type (e.g. the bundled
 * dotenv plugin's `.env file` shadowing `*.t` for `.env.t`).
 */
data class FileTypeConflict(
    val pattern: String,
    val shadowingFileTypeName: String,
    val representativeFileName: String,
)

/**
 * Pure helper (tasks §1.1): builds a representative file name for a wildcard pattern so the
 * detector can ask the platform which file type would actually claim files matching it.
 *
 * The leading `*` of the normalized wildcard form is replaced by a probe stem:
 *  - `*.t` → `probe.t`
 *  - `*.ejs.t` → `probe.ejs.t`
 *
 * Patterns that do not normalize to the wildcard form (anything [HygenPatterns.normalize] rejects)
 * yield `null`. Normalizing first means bare extension tokens (`t`, `ejs.t`) are accepted too.
 */
fun representativeName(pattern: String): String? {
    val normalized = HygenPatterns.normalize(pattern) ?: return null
    // normalize() only ever returns `*.<suffix>` or null, so the prefix is guaranteed.
    return "probe.${normalized.substring(2)}"
}

/**
 * Pure wildcard matcher over file-name glob patterns (`*` is the only wildcard, matching any run
 * of characters; matching is case-insensitive the way [java.lang.String.equalsIgnoreCase] is).
 *
 * Deliberately implemented locally instead of delegating to the platform: the detector runs in
 * unit tests without the IDE, and the matcher must behave identically in both worlds.
 */
object GlobMatcher {

    /** @return whether [fileName] matches [pattern] (`*`-wildcards, case-insensitive). */
    fun matches(pattern: String, fileName: String): Boolean {
        val p = pattern.lowercase()
        val n = fileName.lowercase()
        val parts = p.split("*")
        if (parts.size == 1) return n == p
        // The first part must be a prefix, the last a suffix, the rest must appear in order.
        var cursor = 0
        if (!n.startsWith(parts.first())) return false
        cursor = parts.first().length
        for (i in 1 until parts.size - 1) {
            val idx = n.indexOf(parts[i], cursor)
            if (idx < 0) return false
            cursor = idx + parts[i].length
        }
        return n.lastIndexOf(parts.last()) >= cursor && n.endsWith(parts.last())
    }
}

/**
 * Pure helper for intersection-based conflict detection: builds a concrete file name lying in the
 * intersection of one of OUR normalized patterns ([ourPattern], always `*.<suffix>`) and a foreign
 * pattern ([foreignPattern], `*` anywhere or an exact name).
 *
 * Candidates, in order of preference (all stars filled so the result is a concrete file name):
 *  - every `*` except the last filled with nothing, the last with `probe` + our suffix
 *    (`.env.*` × `*.t` → `.env.probe.t`, `*.env.*` × `*.t` → `.env.probe.t`);
 *  - every `*` filled with `probe` + our suffix (`*.env.*` × `*.t` → `probe.t.env.probe.t`);
 *  - every `*` filled with a bare probe stem (`.env.*` × `*.env` → `.env.probe`);
 *  - the foreign pattern itself, for exact foreign names (`Gemfile.t` × `*.t`).
 *
 * The first candidate that — verified with [GlobMatcher] — matches BOTH patterns wins; `null`
 * when the patterns have no representable intersection (e.g. `*.py` × `*.t`).
 */
internal fun intersectionRepresentative(ourPattern: String, foreignPattern: String): String? {
    val normalized = HygenPatterns.normalize(ourPattern) ?: return null
    val suffix = normalized.substring(1) // `*.t` → `.t`
    val starCount = foreignPattern.count { it == '*' }
    val candidates = if (starCount == 0) {
        listOf(foreignPattern)
    } else {
        listOf(
            buildString {
                foreignPattern.forEachIndexed { index, c ->
                    when {
                        c != '*' -> append(c)
                        index == foreignPattern.lastIndexOf('*') -> append("probe$suffix")
                    }
                }
            },
            foreignPattern.replace("*", "probe$suffix"),
            foreignPattern.replace("*", "probe"),
        )
    }
    return candidates.firstOrNull { candidate ->
        '*' !in candidate && candidate.isNotEmpty() &&
            GlobMatcher.matches(foreignPattern, candidate) &&
            GlobMatcher.matches(normalized, candidate)
    }
}
