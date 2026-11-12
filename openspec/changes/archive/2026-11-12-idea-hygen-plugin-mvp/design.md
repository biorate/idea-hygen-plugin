# Design

## Context

Greenfield IntelliJ IDEA plugin. The language semantics are fixed by `@biorate/hygen` (fork of hygen v6.2.11): a template is YAML frontmatter plus an EJS body; both layers are rendered by the same EJS engine with identity escaping; files named `prompt.js`/`index.js`/`prompt.ts`/`index.ts` inside an action folder are hooks, not templates (the engine's `ignores` list in `src/render.ts`); filename suffixes like `.ejs.t` are convention only — the engine never parses them. See proposal.md for motivation.

## Goals / Non-Goals

**Goals:**
- Single-plugin MVP: file type, lexer/parser, highlighting, Level-1 inspections, key/helper completion for IntelliJ IDEA only.
- Host-language awareness of the body via the rules in `hygen-host-detection`.
- A codebase structured so the "full depth" TODO (JS injection, blessed variables, `runner()` dry-run) can be added without rework.

**Non-Goals:**
- JavaScript injection inside EJS expressions (Level-2) — TODO, post-MVP.
- Completion of template variables from CLI args / `prompt.js` (blessed variables) — TODO, post-MVP.
- Dry-run validation via the embedded `runner()` API (Level-3) — TODO, post-MVP.
- Support for other JetBrains IDEs (PyCharm, WebStorm, …).
- Any changes to `@biorate/hygen` itself.

## Decisions

### D1. Stack: Kotlin + Gradle + IntelliJ Platform Gradle Plugin 2.x
Standard, user-confirmed. Targets IntelliJ IDEA only (`sinceBuild` pinned to a current 2024.x+ baseline; no `untilBuild` initially). Version catalog + `intellijPlatform` dependency blocks.

### D2. One custom lexer for the whole file, not a two-file split
The frontmatter and body are handled by one lexer in states: `FRONTMATTER_DELIM → FRONTMATTER (with EJS islands) → FRONTMATTER_DELIM → BODY (text with EJS islands)`. EJS islands are lexed by a single shared EJS tokenizer used in both states. Alternative (reparse hooks onto YAML lexer for frontmatter) was rejected for MVP: it doubles parser work; YAML highlighting inside frontmatter is desirable but can be layered later via a second-level reparse without changing the PSI contract.

### D3. Body host language via language injection, not a composite PSI
Per the MVP slicing decision: the plugin's own PSI covers frontmatter structure + EJS islands; the body text between tags is injected with the detected host language through the IDEA injection API (`MultiHostInjector` / injectable references). This yields host highlighting for free and keeps our layer small. The alternative (full multi-layer composite PSI) is heavier and reserved for the "full depth" TODO if injection proves limiting.

### D4. Host detection is a pure, testable function
`detectHost(toValue: String?, fileName: String): Language?` — first the last path segment of `to:` (regex on the raw, un-rendered value; EJS in segments is fine), then filename (`hello.js.ejs.t` → strip `.t` → `.ejs` → `js`), then null → plain text. Mapping extension → `Language` uses the platform's `Language.findLanguageByID` / `FileTypeManager`; unknown extensions return null (graceful degradation per spec). No `to:` rendering is ever performed.

### D5. Validation as standard inspections, not annotator-only
All Level-1 checks (frontmatter keys, `inject`+`replace` conflict, anchors without `inject`, unterminated tags/frontmatter, EJS in hook files) are implemented as local inspection classes, so users can configure/suppress them. Hook files (`prompt.js` family) are excluded from template parsing at file-classification level (viewProvider decision), so the "EJS in hook file" check is a plain-text inspection on JS files under `_templates/**`.

### D6. Frontmatter key catalog and helper catalog as data
Known keys and helper member lists live in one Kotlin object (frontmatter keys with descriptions; change-case 3.x and inflection 1.x member name lists, transcribed from the dependency docs). Completion contributors read from this catalog — single source of truth shared by completion and validation (unknown-key check).

## Risks / Trade-offs

- [Frontmatter YAML not fully highlighted in MVP (custom simple tokens)] → Acceptable: keys/values get distinct colors; full YAML layering is a later reparse behind the same PSI.
- [Injection-based host highlighting has limited PSI integration (no cross-language refactoring)] → Matches MVP scope; the TODO layer can upgrade to composite PSI later without changing specs.
- [`to:` value may be fully dynamic (`app/<%= file %>`)] → Spec'd fallback to filename; detection returns null rather than guessing wrong.
- [Helper catalogs can drift from dependencies] → Catalog is one file with a link to the dependency version; TODO includes generating it from `node_modules` type defs in a later change.
- [IntelliJ Platform API churn across releases] → Pin `sinceBuild` baseline; use stable APIs (file types, inspections, injector) only.

## Open Questions

- Exact `sinceBuild` baseline (decided at scaffolding time from the latest IPG 2.x default platform).
- Whether frontmatter completion should also suggest already-used template variables as values (post-MVP; does not change specs).
