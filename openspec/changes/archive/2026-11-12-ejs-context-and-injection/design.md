# Design

## Context

The plugin (v0.1.2) has: lexer/PSI with EJS islands as `EJS_ISLAND` composite nodes, catalogs (`HygenFrontmatterCatalog`, `HygenChangeCaseCatalog`, `HygenInflectionCatalog`), a pure `HygenCompletion.computeProposals(file, caretOffset)` with a platform `CompletionContributor` adapter, a `MultiHostInjector` (from task 4.2) that injects host language into the body, and a hard-won rule: the lexer/highlighter path must never produce gaps or exceptions (the 0.1.2 continuity fix). The fork engine's context model is in `/www/biorate/core/packages/@biorate/hygen/src/context.ts` (defaults + derived `name` variants) and `config-resolver.ts` (nearest `.hygen.js` walk). Real config example: `/www/biorate/application-template/.hygen.js` (`module.exports = { helpers: {...} }`, no `localsDefaults`).

## Goals / Non-Goals

**Goals:**
- A single `HygenTemplateContext` resolver that answers "what identifiers are visible at offset N".
- JS injection into EJS islands with synthetic declarations so nothing false-positive errors.
- Full unit-test coverage against the `application-template` corpus.

**Non-Goals:**
- Executing `.hygen.js` or any user code (no Node bridge in this change).
- Full type inference of helper signatures (callable-or-any is enough).
- Dry-run validation (8.3), `.d.ts`-generated catalogs (8.4), YAML layering (8.5).
- Supporting classic per-generator `prompt.js` variable extraction (no such files in our repos; can be added later as another context source).

## Decisions

**D1 — Parse `.hygen.js`, never execute it.** Use the platform's JavaScript PSI (`JavaScriptLanguage`, available in IDEA Ultimate) to read `module.exports = { ... }`: collect `localsDefaults` keys and `helpers` property names (including getters). Alternative (executing via Node) rejected: pulls 8.3 infrastructure, arbitrary-code-execution risk, and file-watching complexity. Limitation accepted: computed/dynamic helper names are not detected.

**D1a — Prompt hook variables via the same PSI approach.** The action folder's prompt hook is parsed with the same JS/TS PSI: collect `name:` property values from question object literals (in exported arrays and in statically visible object literals inside the hook file), plus statically visible keys of object literals returned by `params(...)`. TS hooks (`prompt.ts`) are parsed with the same PSI (no ts-node). Only statically extractable names are taken — dynamic `name: someVar` is skipped silently (parse, not execute).

**D2 — Context resolution is positional and cached.** `HygenTemplateContext.visibleAt(offset)` merges: engine defaults (constant) → `.hygen.js` sources (cached per virtual file + modification stamp, invalidated on save) → in-template declarations from islands with `island.endOffset <= offset` (PSI walk, O(islands)). Shadowing: later sources win; duplicates collapse in the completion set.

**D3 — Injection via a dedicated `MultiHostInjector` for EJS islands, separate from the body injector.** The existing body injector stays untouched; a new injector registers `EJS_ISLAND` code regions (the leaf runs inside the island, excluding delimiters and `<%`/`%>`) and injects `JavaScriptLanguage`. Raw text inside scriptlets may contain EJS-trim markers (`_%>`, `-%>` on boundaries) — the injector excludes them from ranges (tokenizer already classifies them). Alternative (one combined injector) rejected: couples body and island behavior, harder regression isolation.

**D4 — Synthetic declarations: prelude via placeholders, reference contributor as fallback (spike-verified).** Verified against the actual 252/253 jars: `MultiHostRegistrarPlaceholderHelper` (252 `lib/app-client.jar`, 253 `lib/app.jar`) wraps a `MultiHostRegistrar` and accepts placeholder text pairs (`addGlobalPlaceholders` / `addHostPlaces` with `Pair<TextRange, String>`), injecting prelude text at zero-width ranges — this is the concrete prelude API (no `addPlace` overload with placeholders exists on `MultiHostRegistrar` itself). Chosen mechanism (updated by task 3.2 probe): plain JS injection + `PsiReferenceContributor` (fallback) is the WORKING path — the placeholder helper splits ranges off the host's `getValueTextRange`, and without a registered `ElementManipulator` for `HygenEjsIsland` that includes the delimiters, so placeholders either drop the code or inject `<%=`/`%>` as literal JS. `HygenEjsPlaceholderInjector` is implemented behind a reflective guard but NOT wired; enabling it requires an `ElementManipulator<HygenEjsIsland>` + real-IDE validation (deferred, tracked in tasks.md §4). Verified fallback: `PsiReferenceContributor` registered for the injected JS language fires inside injected fragments (proof: Angular ships `Angular2Injector` + `Angular2TSReferencesContributor` in `plugins/angular/lib/angular.jar`; same pattern in Vue's `VueInjector`/`VueJSReferenceContributor`) — switch to it if the placeholder path fights the JS engine in practice.

**D5 — Unknown helper values are `any`.** Helpers from `.hygen.js` are names only; they resolve as callable-or-any (no parameter checking). Prevents false errors at the cost of not catching wrong arities — acceptable for config files that are hand-written.

**D6 — Unterminated islands are excluded from injection.** The tokenizer already marks `UNTERMINATED_EJS`; the injector skips those nodes entirely (the inspection still fires). Empty islands and whitespace-only islands are skipped.

## Risks / Trade-offs

- **JS plugin API instability across builds** (since-build 243+): the synthetic-declaration spike (D4) is the riskiest part; the reference-contributor fallback is deliberately more conservative API-wise.
- **`.hygen.js` parse fidelity**: dynamic constructs (`Object.assign(module.exports, ...)`, spreads) are invisible — documented limitation, matches "parse, don't execute".
- **Injection + existing body injection stacking**: islands inside the body region could double-inject. Guard: the body injector's ranges only cover `BODY_TEXT` leaves; islands are separate composite nodes — keep it that way and add a regression test asserting no overlapping injection ranges on a corpus sweep.
- **Performance**: `.hygen.js` parse is cached per file+stamp; in-template scan is PSI-local. No index churn.

## Migration / Rollout

Pure additive IDE behavior; no config or file changes for users. Ship as 0.2.0.
