package com.biorate.hygen.lang.injection

import com.biorate.hygen.catalog.HygenChangeCaseCatalog
import com.biorate.hygen.catalog.HygenInflectionCatalog
import com.biorate.hygen.context.HygenContext

/**
 * The synthetic JavaScript prelude for an injected EJS island (design.md D4, spec: synthetic
 * declarations for the template context).
 *
 * The island code that is injected as JavaScript (`<%= h.changeCase.param(name) %>`, `<% %>`
 * scriptlets, …) references identifiers — `h`, `name`/`Name`/`names`/`Names`, the `.hygen.js`
 * helpers (`root`, …), the hook variables and the in-template `const` declarations — that are NOT
 * declared anywhere the JavaScript analyzer knows about, so the injected fragment would otherwise
 * be covered in "unknown symbol" error markers. [build] produces a small JS text that, when
 * prepended to the island's code, declares every one of those identifiers so nothing false-positive
 * errors.
 *
 * The prelude has three parts (design.md D4 / D5):
 *
 *  - the `h` namespace: `changeCase` (all 40 change-case members), `inflection` (all 16 inflection
 *    members), `capitalize` and `path`, each member typed callable-or-any —
 *    `(<...args>) => ''` — so a wrong arity never errors (D5: unknown values are `any`-like);
 *  - one top-level `const X = '';` per visible context [HygenContext.variables] (engine defaults,
 *    `.hygen.js` locals, hook variables, in-template declarations) — `h` is skipped because it is
 *    declared by the `h` namespace above;
 *  - one top-level `const X = (...args) => null;` per surrounding `.hygen.js` helper
 *    ([HygenContext.helpers]) — callable-or-any shape, so both bare `root(...)` and `h.root(...)`
 *    resolve without parameter checking.
 *
 * It is a pure function of a [HygenContext], so it is directly unit-testable; the injector resolves
 * the context per island via [com.biorate.hygen.context.HygenTemplateContext.visibleAt].
 */
object HygenEjsPrelude {

    /** The member names known on `h`, i.e. the built-in top-level helpers plus the config helpers. */
    fun hMemberNames(context: HygenContext): Set<String> = builtinHMembers + context.helpers

    /** The built-in top-level `h` members (engine `src/helpers.ts`, served by the catalogs). */
    val builtinHMembers: Set<String> = setOf("changeCase", "inflection", "capitalize", "path")

    /** Builds the synthetic prelude text for an island at the given visible [context]. */
    fun build(context: HygenContext): String {
        val sb = StringBuilder()
        sb.append("const h = {\n")
        sb.append("  changeCase: {\n")
        for (member in HygenChangeCaseCatalog.members.keys) sb.append("    $member: (...args) => '',\n")
        sb.append("  },\n")
        sb.append("  inflection: {\n")
        for (member in HygenInflectionCatalog.members.keys) sb.append("    $member: (...args) => '',\n")
        sb.append("  },\n")
        // `capitalize` / `path` are callable values; being a function they also carry any property a
        // template might chain off them (e.g. `h.path.join(...)`), so `any`-like behaviour wins.
        sb.append("  capitalize: (...args) => '',\n")
        sb.append("  path: (...args) => '',\n")
        sb.append("};\n")

        for (variable in context.variables) {
            if (variable == "h") continue
            sb.append("const $variable = '';\n")
        }
        for (helper in context.helpers) {
            sb.append("const $helper = (...args) => null;\n")
        }
        return sb.toString()
    }
}
