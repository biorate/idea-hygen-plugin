package com.biorate.hygen.completion

import com.biorate.hygen.catalog.HygenChangeCaseCatalog
import com.biorate.hygen.catalog.HygenFrontmatterCatalog
import com.biorate.hygen.catalog.HygenInflectionCatalog
import com.biorate.hygen.context.HygenContext
import com.biorate.hygen.context.HygenTemplateContext
import com.biorate.hygen.lang.HygenTokenTypes
import com.biorate.hygen.lang.psi.HygenFile
import com.intellij.psi.PsiElement

/**
 * One completion candidate produced by the hygen completion contributors (task 6.2):
 *
 * - [name]: the member / frontmatter key / top-level helper / context variable inserted by the
 *   lookup.
 * - [description]: the one-line tooltip shown for the candidate.
 * - [isNamespace]: true for the `h.changeCase` / `h.inflection` namespaces, which open a nested
 *   completion rather than producing a value on their own.
 */
data class HygenCompletionCandidate(
    val name: String,
    val description: String,
    val isNamespace: Boolean = false,
)

/**
 * Pure, testable completion logic for hygen templates (design.md D6, task 6.2). Both the real
 * [HygenCompletionProvider] and the unit tests drive this single entry point, so the tested
 * computation is exactly the production path.
 *
 * [computeProposals] inspects the PSI at [caretOffset] in [file] and returns the candidates the
 * editor should offer:
 *
 *  - a frontmatter key position -> the known keys from [HygenFrontmatterCatalog];
 *  - inside an EJS island at an identifier position -> the visible context variables (engine
 *    defaults, `.hygen.js` locals, hook variables and in-template declarations) from [context];
 *  - inside an EJS island after `h.` -> the fork's top-level helpers (`changeCase`, `inflection`,
 *    `capitalize`, `path`) exposed by the engine's `src/helpers.ts`, merged with the `.hygen.js`
 *    `helpers` from [context];
 *  - inside an EJS island after `h.changeCase.` -> [HygenChangeCaseCatalog] members;
 *  - inside an EJS island after `h.inflection.` -> [HygenInflectionCatalog] members;
 *  - anything else -> an empty list (no completion is offered).
 *
 * The logic is defensive: it never throws and never inspects outside the PSI node that contains
 * the caret, so completion keeps working on broken templates (spec `hygen-completion`: an
 * unterminated `<%` still yields `h.`-driven completion rather than an error).
 *
 * The context is supplied by the caller (the [HygenCompletionProvider] resolves it from disk via
 * [HygenTemplateContext.visibleAt]); the two-argument overload is a convenience that resolves the
 * same context. This keeps the completion routing pure and the disk-backed resolution in one place.
 */
object HygenCompletion {

    private val OPENING_DELIMITER = Regex("^<%[-=#]?")

    /** Top-level helpers the fork exposes on `h` (engine `src/helpers.ts`). */
    private val topLevelHelpers: List<HygenCompletionCandidate> = listOf(
        HygenCompletionCandidate(
            "changeCase",
            "change-case 3.x string-transform namespace",
            isNamespace = true,
        ),
        HygenCompletionCandidate(
            "inflection",
            "inflection 1.x string-inflection namespace",
            isNamespace = true,
        ),
        HygenCompletionCandidate("capitalize", "upper the first letter of a string"),
        HygenCompletionCandidate("path", "Node.js `path` module"),
    )

    /**
     * Returns the candidates the editor should offer at [caretOffset] in [file].
     * See the class doc for the routing rules.
     *
     * The visible context is resolved here via [HygenTemplateContext.visibleAt]. Prefer the
     * three-argument overload when you already have a [HygenContext].
     */
    fun computeProposals(file: HygenFile, caretOffset: Int): List<HygenCompletionCandidate> =
        computeProposals(file, caretOffset, HygenTemplateContext.visibleAt(file, caretOffset))

    /**
     * Returns the candidates the editor should offer at [caretOffset] in [file] given the already
     * resolved [context] (the identifiers visible at that offset). The production adapter
     * ([HygenCompletionProvider]) resolves [context] from disk and passes it in.
     */
    fun computeProposals(
        file: HygenFile,
        caretOffset: Int,
        context: HygenContext,
    ): List<HygenCompletionCandidate> {
        if (file.text.isEmpty()) return emptyList()
        val element = elementAt(file, caretOffset) ?: return emptyList()
        return when (val island = EjsContext.from(element)) {
            is EjsContext.InIsland -> islandProposals(file, island.islandStart, caretOffset, context)
            EjsContext.InFrontmatter -> frontmatterProposals(file, caretOffset)
            EjsContext.None -> emptyList()
        }
    }

    // ---- helpers ----

    /** Frontmatter keys, offered only at a key-writing position (before `:` on the line). */
    private fun frontmatterProposals(file: HygenFile, caretOffset: Int): List<HygenCompletionCandidate> {
        if (!isKeyPosition(file, caretOffset)) return emptyList()
        return HygenFrontmatterCatalog.keys.entries.map { (key, description) ->
            HygenCompletionCandidate(key, description)
        }
    }

    /**
     * True when the caret sits where a frontmatter key would be typed: no `:` has been typed yet
     * on the current line before the caret. This covers an empty frontmatter line (all keys) and
     * a partially typed key (keys filtered down to the prefix by the editor).
     */
    private fun isKeyPosition(file: HygenFile, caretOffset: Int): Boolean {
        val clamped = caretOffset.coerceIn(0, file.text.length)
        val lineStart = file.text.lastIndexOf('\n', (clamped - 1).coerceAtLeast(0)) + 1
        val linePrefix = file.text.substring(lineStart, clamped)
        return !linePrefix.contains(':')
    }

    /**
     * Completion inside an EJS island. A bare identifier (or an empty prefix) offers the visible
     * context variables; a member chain is only supported for the `h` namespace, so any other
     * dotted expression yields nothing.
     */
    private fun islandProposals(
        file: HygenFile,
        islandStart: Int,
        caretOffset: Int,
        context: HygenContext,
    ): List<HygenCompletionCandidate> {
        val typed = file.text.substring(islandStart, caretOffset.coerceIn(islandStart, file.text.length))
        val expression = typed.replaceFirst(OPENING_DELIMITER, "").trim()
        if (expression == "h" || expression.startsWith("h.")) {
            return helperProposals(expression, context)
        }
        // A dotted expression on anything other than `h` is not a supported completion entry point
        // (no member inference outside the helper namespace).
        if (expression.contains(".")) return emptyList()
        return variableProposals(context)
    }

    /** The context variables offered at a bare-identifier position. */
    private fun variableProposals(context: HygenContext): List<HygenCompletionCandidate> =
        context.variables.map { name ->
            HygenCompletionCandidate(name, "template variable visible in this scope")
        }

    /** Helper-object completion, based on the text typed inside the island before the caret. */
    private fun helperProposals(
        expression: String,
        context: HygenContext,
    ): List<HygenCompletionCandidate> {
        val parts = expression.split(".").map { it.trim() }
        if (parts.isEmpty() || parts.first() != "h") return emptyList()

        return when {
            // `h.` (or `h` alone) -> top-level helpers + the config's `helpers` members.
            parts.size == 1 || (parts.size == 2 && parts[1].isEmpty()) ->
                topLevelHelpers + configHelperProposals(context)
            // `h.changeCase.*` -> change-case members.
            parts.size in 2..3 && parts[1] == "changeCase" -> changeCaseMembers()
            // `h.inflection.*` -> inflection members.
            parts.size in 2..3 && parts[1] == "inflection" -> inflectionMembers()
            else -> emptyList()
        }
    }

    /** The `.hygen.js` `helpers` members (callable-or-any, design.md D5) offered after `h.`. */
    private fun configHelperProposals(context: HygenContext): List<HygenCompletionCandidate> =
        context.helpers.map { name ->
            HygenCompletionCandidate(name, "helper from the project's .hygen.js")
        }

    private fun changeCaseMembers(): List<HygenCompletionCandidate> =
        HygenChangeCaseCatalog.members.entries.map { (name, description) ->
            HygenCompletionCandidate(name, description)
        }

    private fun inflectionMembers(): List<HygenCompletionCandidate> =
        HygenInflectionCatalog.members.entries.map { (name, description) ->
            HygenCompletionCandidate(name, description)
        }

    /** Returns the leaf PSI element at [offset]; clamps an EOF caret to the last element. */
    private fun elementAt(file: HygenFile, caretOffset: Int): PsiElement? {
        if (file.text.isEmpty()) return null
        file.findElementAt(caretOffset)?.let { return it }
        return file.findElementAt((caretOffset - 1).coerceAtLeast(0))
    }

    /** The completion context at a caret, derived by walking the PSI ancestors. */
    private sealed class EjsContext {
        /** Inside an EJS island (or an unterminated tag run); carries the island start offset. */
        data class InIsland(val islandStart: Int) : EjsContext()

        /** Inside the frontmatter block but not inside an EJS island. */
        object InFrontmatter : EjsContext()

        /** Nowhere completion applies (body text that is not an island, etc.). */
        object None : EjsContext()

        companion object {
            /**
             * Walks up from [element] until it reaches an EJS island / unterminated run (-> [InIsland]),
             * the frontmatter block (-> [InFrontmatter]) or the file root (-> [None]). The first of
             * these encountered wins, so a caret inside an island inside a frontmatter value is
             * classified as an island, and a caret in a value that is not an island is frontmatter.
             */
            fun from(element: PsiElement): EjsContext {
                var current: PsiElement? = element
                // Walk up until the caret's containing region is identified. The first of the
                // island / frontmatter markers encountered wins, so an island inside a frontmatter
                // value is classified as an island. Reaching the file root means no known region
                // applies.
                while (current != null) {
                    when (current.node?.elementType) {
                        HygenTokenTypes.EJS_ISLAND,
                        HygenTokenTypes.UNTERMINATED_EJS,
                        -> return InIsland(current.textRange.startOffset)

                        HygenTokenTypes.FRONTMATTER -> return InFrontmatter
                    }
                    current = current.parent
                }
                return None
            }
        }
    }
}
