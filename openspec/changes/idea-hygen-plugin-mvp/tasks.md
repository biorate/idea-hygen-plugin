# Tasks

## 1. Project scaffolding

- [x] 1.1 Initialize the Gradle IntelliJ plugin project: `build.gradle.kts`, settings, version catalog, Kotlin, IntelliJ Platform Gradle Plugin 2.x, `plugin.xml` with a minimal descriptor; verify `./gradlew buildPlugin` succeeds and a sandbox IDEA can load the empty plugin.
- [x] 1.2 Register the `.ejs.t` file type and language (`hygen-template-language`) with a minimal file view provider; verify opening `hello.ejs.t` in the sandbox routes to the new language (no plain-text fallback).

## 2. Lexer and parser

- [x] 2.1 Implement the single-state-machine lexer (frontmatter delimiters, frontmatter region, body region, shared EJS island tokenizer incl. `<%%` escape and `-` trimming); verify token output on fixture templates with a unit test.
- [x] 2.2 Implement the PSI parser (frontmatter block, frontmatter key/value pairs, EJS islands, body text) and the `to:`-value accessor; verify PSI tree shape on fixtures (frontmatter keys, islands, unterminated-tag node).

## 3. Highlighting

- [x] 3.1 Define color settings pages (EJS delimiters, expression, scriptlet, comment, frontmatter delimiters, frontmatter keys/values) and wire syntax highlighting annotator; verify colors in the sandbox against fixtures.

## 4. Host language detection and injection

- [x] 4.1 Implement `detectHost(toValue, fileName)` per `hygen-host-detection` spec with unit tests covering: static `to:`, interpolated segments, fully dynamic `to:`, `hello.js.ejs.t`, `hello.ejs.t`, unknown extension.
- [x] 4.2 Implement `MultiHostInjector` that injects the detected host language into the body region and excludes hook files (`prompt.js`/`index.js`/`prompt.ts`/`index.ts`) from template parsing; verify in sandbox: `to: app/x.js` body shows JS highlighting, `prompt.js` opens as plain JS.

## 5. Validation (Level-1 inspections)

- [x] 5.1 Frontmatter inspections: unknown-key warning (fork catalog), `inject`+`replace` error, anchor options without `inject: true` warning, unterminated frontmatter error; verify with inspection test cases per spec scenarios.
- [x] 5.2 EJS inspections: unterminated tag error, no false positive on `<%%` escape; EJS-in-hook-file warning on JS/TS files under `_templates/**`; verify with inspection tests.
- [x] 5.3 Run the full inspection suite against sample templates from `@biorate/core` (real-world fixtures); confirm zero false positives on the package's own `src/templates/**`.

## 6. Completion

- [x] 6.1 Build the shared catalog object (frontmatter keys with descriptions; change-case 3.x and inflection 1.x member lists) with unit tests on catalog integrity.
- [x] 6.2 Implement frontmatter key completion contributor and helper completion (`h.`, `h.changeCase.`, `h.inflection.`) inside EJS islands; verify completion still works on a template with an unterminated tag (graceful degradation test).

## 7. Integration verification

- [ ] 7.1 End-to-end sandbox pass: open `_templates` fixtures from a real project, exercise highlighting, detection, each inspection, and completion; record results.
- [ ] 7.2 Build the distributable plugin (`./gradlew buildPlugin`), install into the local IDEA via IDEA MCP / sandbox, and confirm all spec scenarios behave as written.

## 8. Post-MVP TODO (tracked here, out of MVP scope)

- [ ] 8.1 TODO: JavaScript injection inside EJS expressions (Level-2) — real completion and error highlighting in `<%= %>` bodies.
- [ ] 8.2 TODO: completion of blessed variables (`name` → `Name`) and variables parsed from the action folder's `prompt.js`.
- [ ] 8.3 TODO: dry-run validation via the embedded `runner()` API of `@biorate/hygen` (Level-3).
- [ ] 8.4 TODO: generate helper catalogs from `node_modules` type definitions instead of a transcribed list.
- [ ] 8.5 TODO: full YAML layering of the frontmatter (second-level reparse).

## Workflow follow-up

- Merge/archive the change after MVP verification: `openspec archive idea-hygen-plugin-mvp`.
