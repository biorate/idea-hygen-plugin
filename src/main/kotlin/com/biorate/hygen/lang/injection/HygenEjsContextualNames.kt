package com.biorate.hygen.lang.injection

import com.biorate.hygen.catalog.HygenChangeCaseCatalog
import com.biorate.hygen.catalog.HygenInflectionCatalog
import com.biorate.hygen.context.HygenContext

/**
 * Pure decision logic for [HygenEjsReferenceContributor] (design.md D4 fallback, spec: synthetic
 * declarations for the template context).
 *
 * When an EJS island's code is injected as JavaScript, the fragment references template-context
 * identifiers — the `h` namespace, the engine's `name` variants, the `.hygen.js` helpers — that are
 * NOT declared anywhere the JavaScript analyzer knows about. Without declarations those would be
 * flagged "unknown symbol". The reference contributor suppresses that by handing the JS engine a
 * resolving reference for the ROOT of any chain that names a known template-context identifier, so
 * `h.changeCase.param(name)` and `root('...')` render without error markers.
 *
 * This object is the single source of truth for WHAT resolves (mirroring [HygenEjsPrelude], the text
 * that the placeholder path would inject): the `h` namespace plus its built-in and config members,
 * and the visible context variables/helpers. [resolveRoot] is a pure predicate of a name and a
 * published [HygenContext], so the reference logic is directly unit-testable without an IDE.
 */
object HygenEjsContextualNames {

    /** The built-in groups served by the member catalogs (engine `src/helpers.ts`). */
    val builtinGroups: Set<String> = setOf("changeCase", "inflection")

    /** All member names reachable under `h` (built-ins + the `.hygen.js` helpers). */
    fun hMembers(context: HygenContext): Set<String> =
        HygenEjsPrelude.builtinHMembers + context.helpers

    /**
     * True when the root identifier [name] of a reference chain is a known template-context
     * identifier and should therefore resolve (design.md D4/D5).
     *
     * - `h` always resolves (the helper namespace);
     * - a member of the `h` namespace (`h.changeCase`, `h.path`, …) is contextual when its name is
     *   a known group/member;
     * - a bare top-level identifier resolves when it is a visible context variable (engine
     *   defaults, locals, hook vars, in-template declarations) or a `.hygen.js` helper, which the
     *   engine exposes both as `h.<helper>` and bare (callable-or-any, D5).
     *
     * @param name the reference expression's referenced name (the root of a chain).
     * @param parentChain the chain of qualifier names above [name], outermost first; empty for a
     *   bare global (`name`, `root`) and `["h"]` for `h.changeCase.param`'s `changeCase` element.
     */
    fun resolveRoot(context: HygenContext, name: String, parentChain: List<String>): Boolean {
        if (name == "h" && parentChain.isEmpty()) return true
        return when {
            parentChain.isEmpty() -> name in context.variables || name in context.helpers
            // `h.<name>` (and deeper) — group/member of the helper namespace.
            parentChain.first() == "h" -> hMembers(context).contains(name)
            else -> false
        }
    }

    /**
     * True when [name] is a known member directly under the `h` group [group], i.e. a value the
     * JS analyzer should treat as callable-or-any rather than unknown. Used by the reference
     * contributor to also cover the leaf of `h.changeCase.param` / `h.inflection.member`.
     */
    fun isGroupMember(group: String, name: String): Boolean = when (group) {
        "changeCase" -> HygenChangeCaseCatalog.isKnown(name)
        "inflection" -> HygenInflectionCatalog.isKnown(name)
        else -> false
    }
}
