# Proposal

## Why

The template extension is hard-coded to `*.ejs.t` (registered via `patterns="*.ejs.t"` in `plugin.xml`). Real projects use different conventions — plain `.t` (as in `application-template`'s `00009.00_.prettierrc.t`-style names the user works with), `.tmpl`, or other suffixes — and the plugin should follow the project, not force its own. A configurable pattern list turns the plugin into the general hygen-template tool for any naming convention.

## What Changes

- Add plugin settings (IDE `Configurable` page, `PersistentStateComponent`, application-level) holding the list of template filename patterns, defaulting to `*.t` (replacing the current hard-coded `*.ejs.t`).
- Apply the configured patterns dynamically: on settings change and on IDE startup, associate/dissociate `Hygen Template` file-type associations via the platform `FileTypeManager` API instead of a fixed `patterns=` attribute.
- Ship preset choices in the settings UI for convenience: `*.t` (default), `*.ejs.t`, `*.tmpl` — plus free-form user patterns (wildcard patterns, validated).
- Keep recognition conservative in one respect: a pattern must be a filename pattern (leading `*.` or literal suffix); invalid patterns are rejected with a validation message in the UI.
- Update all affected tests (pattern matchers, file-type resolution, PSI fixture naming) and bump the plugin version.

## Capabilities

### Modified Capabilities
- `hygen-template-language`: the "Template file type recognition" requirement changes — recognition is driven by user-configurable filename patterns (default `*.t`) instead of the hard-coded `.ejs.t` pattern.

### New Capabilities
<!-- none: settings storage/UI is implementation detail owned by the recognition requirement -->

## Impact

- `plugin.xml`: `patterns="*.ejs.t"` removed/replaced by minimal static registration + dynamic association; new `applicationConfigurable` + `projectService`/`applicationService` for settings.
- `HygenFileType.kt` (pattern constant), `HygenFileTypeOverriderTest`, `HygenPsiTest`, new settings/configurable classes + tests.
- User-visible: Settings → Tools → Hygen Templates (pattern list); default switches recognition from `*.ejs.t` to `*.t` (deliberate, user-requested behavior change).
- Risk: claiming `*.t` globally takes the extension from other plugins (e.g. Perl) — accepted by the user; setting stays editable to revert.
