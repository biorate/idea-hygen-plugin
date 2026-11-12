# Proposal

## Why

Templates currently get generic highlighting and a fixed helper catalog: the IDE does not know which variables a template can reference, so completion offers only frontmatter keys and `h.*` members, and EJS expressions are plain text. The fork engine (`@biorate/hygen` `context.ts`) builds a well-defined context — engine defaults, `.hygen.js` config (`localsDefaults` + `helpers`), CLI locals with derived `name` variants, and in-template declarations — and injecting that knowledge makes the plugin's completion and expression highlighting real instead of cosmetic.

## What Changes

- Build a template-context model: engine defaults (`name: 'unnamed'` + derived `Name`, `names`, `Names`), variables parsed from the nearest `.hygen.js` (`localsDefaults`, helper names under `h`), variables extracted from the action folder's prompt hook (`prompt.js`/`prompt.ts`/`index.js`/`index.ts`/`prompt.cjs` — inquirer question names and hook params), and variables declared inside the template itself (assignments in EJS scriptlets/expression-statements), resolved positionally (only declarations before the caret).
- Complete context variables inside EJS islands (bare identifiers), on top of the existing `h.*` completion.
- Inject JavaScript into EJS expression/scriptlet islands (`<%= %>`, `<% %>`) so expressions get JS highlighting, error highlighting, and completion.
- Resolve `h.*` inside injected JS with synthetic declarations: built-in helpers from the existing catalogs (task 6.1) plus `helpers` from `.hygen.js`, so `h.changeCase.param(...)` and project helpers like `root(...)` do not produce "unknown symbol" errors.
- Graceful degradation everywhere: missing/unparsable `.hygen.js`, unterminated EJS tags, and unknown constructs must never throw or blank the editor (lesson from the lexer-continuity bug).

Out of scope (follow-up changes): dry-run validation via `runner()` (8.3), catalogs generated from type definitions (8.4), full YAML frontmatter layering (8.5).

## Capabilities

### New Capabilities
- `hygen-template-context`: the model of variables and helpers visible to a template — sources (engine defaults, `.hygen.js`, in-template declarations), positional resolution rules, and how the context feeds completion.
- `hygen-ejs-injection`: JavaScript injection into EJS expression/scriptlet islands with synthetic `h`/locals declarations, and degradation rules.

### Modified Capabilities
<!-- none: existing capabilities (completion catalog, host detection, validation) keep their requirements -->

## Impact

- New Kotlin: context resolver (PSI-based `.hygen.js` parse, in-template declaration scan), injection contributor + synthetic declaration provider; wiring in `plugin.xml`.
- Depends on: existing PSI/EJS islands (`com.biorate.hygen.lang.psi`), catalogs (`com.biorate.hygen.catalog`), completion (`HygenCompletion`).
- Platform dependencies: JavaScript PSI/plugin (already shipped in IDEA Ultimate), multi-host injection infrastructure from task 4.2 patterns.
- Tests: new suites for context resolution and injection; regression on the `application-template` corpus.
