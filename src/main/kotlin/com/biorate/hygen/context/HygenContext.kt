package com.biorate.hygen.context

/**
 * The identifiers visible to a hygen template at one point in a file (design.md D2, task 2.1).
 *
 * - [variables]: the scalar names offered at the top level of an EJS island. These come from the
 *   engine defaults, the nearest `.hygen.js` `localsDefaults` keys, the action folder's prompt-hook
 *   question/`params` names, and the template's own in-island declarations. The `h` namespace is
 *   always present, so it is included here too.
 * - [helpers]: the extra members offered after `h.` — the property names of the nearest `.hygen.js`
 *   `helpers` object literal. The plugin's built-in `h` members (`changeCase`, `inflection`,
 *   `capitalize`, `path`) are served by the existing completion catalogs, not by this context.
 *
 * Duplicate names collapse to a single element (later sources shadow earlier ones by name).
 */
data class HygenContext(
    val variables: Set<String>,
    val helpers: Set<String>,
) {
    /** True when no identifier at all is visible (only reached on a broken resolver). */
    val isEmpty: Boolean get() = variables.isEmpty() && helpers.isEmpty()
}

/**
 * The engine defaults every template can reference regardless of any config file (the fork's
 * `context.ts`): `name` is `'unnamed'`, and the engine derives the capitalized and pluralized
 * variants for `name`. The `h` namespace object is always available.
 */
object EngineDefaults {
    /** The derived `name` variants the engine creates in `context.ts` (capitalize + pluralize). */
    val names: Set<String> = setOf("name", "Name", "names", "Names")

    /** The always-present context: the `name` variants plus the `h` namespace. */
    val context: HygenContext = HygenContext(
        variables = names + "h",
        helpers = emptySet(),
    )
}
