# Design

## Context

v0.2.0 registers the Hygen file type statically via `<fileType patterns="*.ejs.t"/>` (the compound-pattern fix from 0.1.1). The user works with projects using plain `.t` files and wants the extension configurable: presets `.t` (default), `.ejs.t`, `.tmpl`, plus free-form. Platform facts already verified in this repo: file-type `patterns=` matching goes through `WildcardFileNameMatcher` (`*.t` matches by full name); `FileTypeManager` supports runtime association changes.

## Goals / Non-Goals

**Goals:**
- Pattern list stored in persistent plugin settings; default `*.t`.
- Associations applied dynamically at startup and on settings change — no restart.
- Settings page with presets + free-form editing + validation.
- Pure logic (pattern normalization/validation/matching) unit-testable without the IDE.

**Non-Goals:**
- Project-level (per-project) pattern overrides — application-level setting only for now.
- Content-based template detection (e.g. claiming `_templates/**` files of ANY extension) — already handled separately by the existing `_templates/` overrider logic and stays untouched.
- Migrating stored settings from previous versions (no settings existed before).

## Decisions

**D1 — Runtime associations instead of a static `patterns=` attribute.** `plugin.xml` keeps the `<fileType>` declaration but without `patterns` (or with a minimal no-op anchor); the plugin's `HygenTemplatePatterns` component applies `FileTypeManager.getInstance().associatePattern(HygenFileType.INSTANCE, pattern)` for each configured pattern and removes associations (via `FileTypeManager.removeAssociation` / re-associate diff) when settings change. Rationale: `patterns=` is static for the plugin lifecycle; user edits must apply live. Verified platform API exists for both directions. Alternative (registering N file types) rejected: one file type with N associations is the platform-intended model.

**D2 — Settings: application-level `PersistentStateComponent`.** `HygenTemplateSettings` (application service, `@State` with `Storage("hygenTemplates.xml")`) holds `List<String> patterns`, default `["*.t"]`. An `ApplicationSettingsContributor`-style `Configurable` (`applicationConfigurable` in plugin.xml, Settings → Tools → Hygen Templates) renders an editable list with preset buttons (`*.t`, `*.ejs.t`, `*.tmpl`). `apply()` validates, stores, fires a settings-changed message (`Topic`), and the associations component re-syncs. Alternative (project-level) deferred — templates conventions rarely differ per project inside one IDE.

**D3 — Pattern normalization and validation (pure code).** `HygenPatterns` (pure object): accepts entries of form `*.<ext...>` (one leading wildcard covering arbitrary prefixes, e.g. `*.t`, `*.ejs.t`, `*.tmpl`) or a plain extension token `t`/`ejs.t` normalized to `*.t`/`*.ejs.t`. Rejects empty strings, patterns without `*.` or a valid suffix, patterns with a `/` or additional mid-string wildcards. This mirrors `WildcardFileNameMatcher` semantics and keeps validation testable. The settings UI reuses the same validator for its error messages.

**D4 — Default `*.t` side effects, documented.** With the default, the plugin claims all `.t` files IDE-wide (taking the extension from e.g. the Perl plugin). Accepted by explicit user request. A user reverts by removing `*.t` from the list; empty list disables extension-based claiming entirely (files under `_templates/` remain recognized via the existing overrider).

**D5 — Startup timing.** Associations are re-applied from stored settings on `ApplicationInitializedListener` (and after settings change). If `FileTypeManager` is not yet ready at that point, defer via `ApplicationManager.invokeLater` — must be idempotent.

## Risks / Trade-offs

- Claiming `*.t` globally may surprise users of other `.t` tools — mitigated by editable setting (D4).
- Association diffing on settings change must remove stale associations precisely, else uninstalled patterns keep claiming files — covered by a dedicated test through a fake/mockable `FileTypeManager` seam.
- Dynamic associations are persisted by the IDE itself (file-type associations survive restarts) — re-applying on startup must be idempotent to avoid duplicate associations.

## Migration / Rollout

Version bump to 0.3.0 (behavior change: default recognition moves from `*.ejs.t` to `*.t`). No settings migration needed (first settings release). Spec `hygen-template-language` requirement "Template file type recognition" is MODIFIED accordingly.
