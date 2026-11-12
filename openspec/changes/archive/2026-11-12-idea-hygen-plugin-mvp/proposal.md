# Proposal

## Why

`@biorate/hygen` templates (`.ejs.t`) are plain text for IntelliJ IDEA: no highlighting of EJS tags, no structure in the YAML frontmatter, no validation of frontmatter keys (including this fork's own `create_if_missing`, `replace`, `skip_if_not`), and no completion for frontmatter keys or `h.*` helpers. Writing generators is error-prone because template mistakes surface only at `hygen` runtime.

## What Changes

- Create a new IntelliJ IDEA plugin (Kotlin, Gradle, IntelliJ Platform Gradle Plugin 2.x), targeting IntelliJ IDEA only.
- Introduce a hybrid template language support for `.ejs.t` files: a YAML frontmatter layer plus a body layer with EJS tag islands (`<%= %>`, `<%- %>`, `<% %>`, `<%# %>`, `<%%` escape, `-` trimming).
- Host-language awareness of the body: detected from the `to:` frontmatter value (last path segment extension, no rendering needed), falling back to the template filename (strip `.t`, then `.ejs`, take the remaining extension); unknown → plain text, EJS tags still highlighted.
- File classification inside `_templates/**`: `prompt.js` / `index.js` / `prompt.ts` / `index.ts` are ordinary JS/TS (matching the engine's `ignores` list), everything else is a hygen template.
- Syntax highlighting for EJS tags and frontmatter structure.
- Level-1 validation (structural): broken/unclosed EJS tags, broken YAML frontmatter, unknown frontmatter keys, mutually exclusive `inject` vs `replace`, anchor options (`before`/`after`/`at_line`) without `inject: true`, misuse of EJS inside `prompt.js`-family files.
- Autocompletion: frontmatter keys (fork semantics), `h.changeCase.*` / `h.inflection.*` helpers (change-case 3.x, inflection 1.x), and built-in helpers such as `h.capitalize`.
- TODO (post-MVP, "full depth"): JavaScript injection inside EJS expressions; completion of blessed variables (`name` → `Name`) and variables from `prompt.js`; dry-run validation via the embedded `runner()` API.

## Capabilities

### New Capabilities

- `hygen-template-language`: file type, lexer/parser and syntax highlighting for the hybrid frontmatter + EJS body template.
- `hygen-host-detection`: rules for determining the host language of the template body (`to:` extension → filename convention → plain text) and for classifying hook files (`prompt.js` family) as plain JS/TS.
- `hygen-validation`: Level-1 structural inspections of templates (frontmatter structure and fork-specific keys, EJS tag integrity, hook-file misuse).
- `hygen-completion`: completion for frontmatter keys and `h.*` helper members.

### Modified Capabilities

- (none — greenfield project)

## Impact

- New Gradle IntelliJ plugin project in this repository (Kotlin, IntelliJ Platform Gradle Plugin 2.x, `sinceBuild` targeting current IDEA releases).
- Depends on the documented semantics of `@biorate/hygen` (fork of hygen v6.2.11): identity escaping for `<%= %>`, frontmatter rendered by the same EJS engine, `ignores` list in `src/render.ts`, inject options from the fork.
- No changes to `@biorate/hygen` itself.
