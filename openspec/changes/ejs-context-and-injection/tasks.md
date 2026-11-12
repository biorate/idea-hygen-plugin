# Tasks

## 1. Spike: synthetic declarations mechanism

- [x] 1.1 Spike (≤1 day): verify the chosen mechanism for injecting JS with synthetic prelude declarations inside EJS islands (per design D4); if the prelude path fights the platform API beyond the budget, record the switch to the reference-contributor fallback and update design.md before continuing. — VERIFIED: `MultiHostRegistrarPlaceholderHelper` available on 252/253; fallback (`PsiReferenceContributor` on injected fragments) proven by Angular/Vue bundled plugins; design D4 updated with verified API facts.

## 2. Template context (stage A, 8.2)

- [x] 2.1 Implement `HygenTemplateContext`: engine defaults (`name`, `Name`, `names`, `Names`, `h`), nearest-`.hygen.js` resolver (walk-up per engine `config-resolver`), JS-PSI extraction of `localsDefaults` keys and `helpers` member names (D1), prompt-hook extraction (D1a: `name:` keys of question literals in `prompt.js`/`prompt.ts`/`index.js`/`index.ts`/`prompt.cjs`, first existing per engine order), positional in-template declaration scan (D2); unit tests for each source incl. shadowing, missing/unparsable config, and dynamic question names skipped.
- [x] 2.2 Extend `HygenCompletion` to offer context identifiers inside EJS islands (merge with existing `h.*` catalog completion); tests: defaults-only file, `.hygen.js`-backed file, in-template declaration below/above caret, graceful degradation on unterminated tag.

## 3. JS injection (stage B, 8.1)

- [x] 3.1 Implement the EJS-island JS injector (separate `MultiHostInjector` per D3): inject code regions of `<%`, `<%=`, trimming variants; skip frontmatter, body text, `UNTERMINATED_EJS`, empty islands; regression test asserting no overlap with the body injector's ranges on the `application-template` corpus.
- [x] 3.2 Wire synthetic declarations (per spike outcome): `h` with catalog members + `.hygen.js` helpers (callable-or-any per D5), context variables; tests: `h.changeCase.param(name)` and project helper `root(...)` resolve without error markers; unterminated tag keeps editor stable and inspection firing.

## 4. Integration verification

- [x] 4.1 Full suite green + corpus sweep (`application-template` `_templates`, 187 files): no exceptions, no cascading errors, injection ranges valid; record results in VERIFICATION.md.
- [x] 4.2 Build 0.2.0 (`./gradlew buildPlugin`), install into local IDEA 2025.2, manual sandbox pass: completion of variables in real templates, JS highlighting in islands, no false errors on `.hygen.js` helpers. — built and installed; manual sandbox checklist in VERIFICATION.md §E pending user's IDE pass.

## 5. Post-change backlog (not in this change)

- [ ] 5.1 TODO (next change): dry-run validation via `runner()` (8.3) — Node bridge, sandboxed execution.
- [ ] 5.2 TODO (next change): generate helper catalogs from type definitions (8.4).
- [ ] 5.3 TODO (backlog): full YAML layering of frontmatter (8.5).
