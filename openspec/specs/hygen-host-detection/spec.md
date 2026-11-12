# hygen-host-detection Specification

## Purpose
Defines how the plugin determines the host language of a template's body and how files inside a generator folder are classified between hygen templates and ordinary hook files.

## Requirements

### Requirement: Host language from `to:` frontmatter value
When the frontmatter contains a `to:` key, the plugin SHALL determine the body's host language from the file extension of the last path segment of the `to:` value, regardless of whether the earlier path segments are EJS-interpolated. Rendering the `to:` value is not required for detection.

#### Scenario: Static target extension
- **WHEN** frontmatter contains `to: app/emails/report.js` and the body is a hygen template
- **THEN** the body is treated as JavaScript for host-language purposes

#### Scenario: Interpolated path segments
- **WHEN** frontmatter contains `to: app/<%= section %>/<%= name %>.ts`
- **THEN** the host language is TypeScript, derived from the last segment's extension

#### Scenario: Fully dynamic target
- **WHEN** frontmatter contains `to: app/<%= fileName %>` with no extractable extension
- **THEN** detection falls back to the filename convention

### Requirement: Host language from template filename convention
When the `to:` key is absent or yields no extension, the plugin SHALL derive the host language from the template filename by removing the trailing `.t` and then `.ejs` suffixes and taking the remaining extension as the host language; if nothing remains, the body is plain text with EJS tags still highlighted.

#### Scenario: Conventional suffixed filename
- **WHEN** the template file is named `hello.js.ejs.t`
- **THEN** the body's host language is JavaScript

#### Scenario: Unconventional filename
- **WHEN** the template file is named `hello.ejs.t` and `to:` is absent
- **THEN** the body is treated as plain text; EJS tags are still highlighted

### Requirement: Hook files are not templates
The plugin SHALL treat files named `prompt.js`, `index.js`, `prompt.ts`, and `index.ts` inside a generator action folder as ordinary JavaScript/TypeScript files, matching the engine's ignore list, and SHALL NOT apply template parsing or template validation to them.

#### Scenario: prompt.js in an action folder
- **WHEN** `_templates/mygen/new/prompt.js` is opened
- **THEN** the file is edited and highlighted as plain JavaScript, without frontmatter or EJS parsing

### Requirement: Unknown host language degrades gracefully
The plugin SHALL NOT reject or mis-highlight a template whose host language cannot be determined or is not among the supported host languages; the body SHALL fall back to plain text while EJS tags remain highlighted.

#### Scenario: Unsupported extension
- **WHEN** the detected host extension has no registered language in the IDE
- **THEN** the body renders as plain text with EJS tag highlighting and no error is reported
