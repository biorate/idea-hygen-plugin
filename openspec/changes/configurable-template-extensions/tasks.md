# Tasks

## 1. Pattern engine (pure logic)

- [x] 1.1 Implement `HygenPatterns` (pure object per design D3): normalization (`t` → `*.t`), validation (reject empty, `abc` without wildcard/suffix form, paths with `/`, mid-string wildcards), matching via `WildcardFileNameMatcher` semantics; unit tests: valid/invalid matrices, `*.t` matches `report.t` and `00009.00_x.t`, `*.ejs.t` still matches compound, `*.tmpl` matches `c.tmpl` not `c.tmplx`.

## 2. Settings

- [x] 2.1 Implement `HygenTemplateSettings` (application `PersistentStateComponent`, default `["*.t"]`) and the Settings page (`applicationConfigurable`, Tools → Hygen Templates): editable pattern list, preset buttons (`*.t`, `*.ejs.t`, `*.tmpl`), validation via `HygenPatterns` with visible error messages, `apply()` stores + fires settings-changed topic; wire `plugin.xml` registrations.
- [x] 2.2 Implement association sync: on settings change and on startup (`ApplicationInitializedListener`, idempotent, deferred via `invokeLater` per D5) — associate/dissociate `HygenFileType` patterns to match stored settings exactly (stale patterns removed); unit tests with a seam over `FileTypeManager` (associate/remove called correctly on add/remove/change).

## 3. Rework static registration and tests

- [x] 3.1 Remove `patterns="*.ejs.t"` from `plugin.xml`'s fileType declaration (keep declaration anchor), update `HygenFileType.FILE_NAME_PATTERN` usages to the new pattern-engine path; update `HygenFileTypeOverriderTest`, `HygenPsiTest`, and any fixture naming assumptions; verify `_templates/` overrider behavior unchanged.
- [x] 3.2 Regression: corpus sweep tests still green (`application-template`, `@biorate/hygen`); file resolution tests now driven by default `*.t` setting.

## 4. Release

- [x] 4.1 Bump version 0.3.0; `./gradlew test buildPlugin` green; install into local IDEA 2025.2; manual check: default claims `.t` files, switching preset to `*.ejs.t` releases `.t` without restart, invalid pattern shows validation error. — built and installed; the three manual checks remain for the user's IDE pass.
