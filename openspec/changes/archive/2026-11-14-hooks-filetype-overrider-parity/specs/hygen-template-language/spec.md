# Spec Delta

## MODIFIED Requirements

### Requirement: Template file type recognition

The plugin SHALL recognize hygen templates by a user-configurable list of filename patterns, stored in plugin settings and defaulting to `*.t`. Patterns are applied through the platform file-type association API on startup and whenever settings change, so a change takes effect without IDE restart. The settings UI SHALL offer presets `*.t` (default), `*.ejs.t`, `*.tmpl` and accept custom wildcard filename patterns; invalid or non-filename patterns SHALL be rejected with a visible validation message and not applied. The previous hard-coded `.ejs.t` recognition is replaced by the default `*.t`.

In addition to pattern-matched template files, the plugin SHALL recognize non-pattern template files that live under a project-local `_templates` directory as hygen templates, EXCEPT for the engine's hook files: files named `hooks.ts`, `hooks.js`, `hooks.mjs`, `hooks.cjs`, `prompt.js`, `prompt.ts`, `index.js`, `index.ts` (the hook-module ignore set of `@biorate/hygen`) SHALL keep their ordinary file type, because the engine executes hook modules rather than rendering them.

#### Scenario: Extension-based recognition
- **WHEN** a file matching a configured filename pattern (by default `*.t`, e.g. `report.t` or `00009.00_.prettierrc.ejs.t`) is opened in the editor
- **THEN** the file is handled as a hygen template with the hybrid frontmatter + EJS structure

#### Scenario: Default pattern claims .t files
- **WHEN** the plugin is installed with default settings and a file named `report.t` is opened
- **THEN** the file is handled as a hygen template with the hybrid frontmatter + EJS structure

#### Scenario: Pattern change applies immediately
- **WHEN** the user adds `*.tmpl` in Settings → Tools → Hygen Templates and applies
- **THEN** a file named `component.tmpl` opens as a hygen template without IDE restart

#### Scenario: Preset selection
- **WHEN** the user picks the `*.ejs.t` preset
- **THEN** only files matching `*.ejs.t` are claimed and a plain `.t` file is no longer claimed by the plugin

#### Scenario: Invalid pattern rejected
- **WHEN** the user enters a pattern without a wildcard/suffix form (e.g. `abc`) or an empty entry
- **THEN** the settings page shows a validation error and the pattern is not applied

#### Scenario: Plain-text fallback is not required
- **WHEN** a template file contains no frontmatter delimiters (`---`)
- **THEN** the entire file content is treated as the template body and still parsed for EJS tags

#### Scenario: Hook file under _templates keeps its TypeScript type
- **WHEN** a file named `hooks.ts` exists under `_templates/create/root/` and the platform resolves its file type
- **THEN** the file is NOT overridden to the Hygen Template type and remains a TypeScript file, so the IDE's TypeScript service analyzes it normally (no "not included in tsconfig.json" or "Unresolved variable" warnings caused by the plugin)

#### Scenario: Non-hook file under _templates is still a template
- **WHEN** a file named `helpers.js` exists under `_templates/create/root/`
- **THEN** the file is overridden to the Hygen Template type and opens with the hybrid frontmatter + EJS structure
