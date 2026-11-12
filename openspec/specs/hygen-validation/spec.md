# hygen-validation Specification

## Purpose
Defines Level-1 (structural, static) validation for hygen templates: frontmatter structure and fork-specific keys, EJS tag integrity, and misuse of template syntax in hook files.

## Requirements

### Requirement: Frontmatter key validation
The plugin SHALL validate frontmatter keys against the known set of the `@biorate/hygen` fork — including `to`, `inject`, `sh`, `before`, `after`, `prepend`, `append`, `at_line`, `skip_if`, `skip_if_not`, `create_if_missing`, `replace`, `unless_exists`, `local`, `setup`, `force`, `message`, `echo`, `spinner`, `sh_ignore_exit`, `mode`, `verbose`, `from`, and `eof_last` — and SHALL report unknown keys as warnings (not errors), since arbitrary keys may serve as template variables.

#### Scenario: Fork-specific keys accepted
- **WHEN** frontmatter contains `create_if_missing: true` and `replace: true`
- **THEN** neither key is reported as unknown

#### Scenario: Unknown key warns
- **WHEN** frontmatter contains `too: app/x.js` (typo)
- **THEN** a warning names the unknown key, without suppressing other inspections

### Requirement: Mutually exclusive inject options
The plugin SHALL report an error when both `inject: true` and `replace: true` are present in the frontmatter, because the fork treats them as mutually exclusive write modes.

#### Scenario: inject together with replace
- **WHEN** frontmatter contains both `inject: true` and `replace: true`
- **THEN** an error inspection is reported on the conflicting keys

### Requirement: Anchor options require inject or replace mode
The plugin SHALL report a warning when an anchor option (`before`, `after`, `prepend`, `append`, `at_line`) is present but ignored — i.e. the action is in plain add mode with neither `inject: true` nor `replace: true`. Anchors are meaningful for the inject operation, and the fork engine also treats `before`/`after` as the replace anchors (`ops/injector.ts` `REPLACE_ANCHORS`), so only in add mode is an anchor ignored entirely.

#### Scenario: before without inject or replace
- **WHEN** frontmatter contains `before: "// anchor"` with neither `inject: true` nor `replace: true`
- **THEN** a warning explains that the anchor is ignored in add mode

#### Scenario: before with replace
- **WHEN** frontmatter contains `before: "// anchor"` together with `replace: true`
- **THEN** no warning is reported, because `before` is the replace anchor used by the fork engine

### Requirement: EJS tag integrity
The plugin SHALL report an error for an unterminated EJS tag (an opening `<%` without a matching closing `%>`) and SHALL NOT report an error for the `<%%` literal-escape sequence.

#### Scenario: Unterminated tag
- **WHEN** the body contains `<%= name` with no closing `%>`
- **THEN** an error inspection marks the unterminated tag

#### Scenario: Escaped literal
- **WHEN** the body contains `<%%= name %>`
- **THEN** no error is reported; the sequence is treated as a literal

### Requirement: EJS syntax in hook files is flagged
The plugin SHALL report a warning when EJS tag syntax appears in a hook file (`prompt.js`, `index.js`, `prompt.ts`, `index.ts`) inside a generator action folder, because hook files are executed as JavaScript and are not rendered.

#### Scenario: Template pasted into prompt.js
- **WHEN** `_templates/mygen/new/prompt.js` contains `<%= name %>`
- **THEN** a warning indicates that EJS tags are not rendered in hook files

### Requirement: Broken frontmatter delimiter
The plugin SHALL report an error when a frontmatter block is opened by a `---` line but never closed by a matching `---` line at the same nesting level.

#### Scenario: Missing closing delimiter
- **WHEN** a template starts with `---` and no second `---` line exists
- **THEN** an error inspection reports the unterminated frontmatter block
