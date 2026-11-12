# hygen-template-language Specification

## Purpose
Defines the syntax model and highlighting behavior for `@biorate/hygen` template files (`.ejs.t`): a hybrid file with a YAML frontmatter header and an EJS-in-text body, recognized and colored inside IntelliJ IDEA.

## Requirements

### Requirement: Template file type recognition
The plugin SHALL register a dedicated file type for `@biorate/hygen` templates, recognized by the `.ejs.t` filename pattern and by files located under a project-local `_templates/` directory.

#### Scenario: Extension-based recognition
- **WHEN** a file with the `.ejs.t` extension is opened in the editor
- **THEN** the file is handled as a hygen template with the hybrid frontmatter + EJS structure

#### Scenario: Plain-text fallback is not required
- **WHEN** a template file contains no frontmatter delimiters (`---`)
- **THEN** the entire file content is treated as the template body and still parsed for EJS tags

### Requirement: EJS tag syntax highlighting
The plugin SHALL highlight the EJS tag families `<%= %>`, `<%- %>`, `<% %>`, `<%# %>`, and the `<%%` escape sequence, including the optional `-` trimming marker adjacent to the closing delimiter, with distinct editor colors.

#### Scenario: Expression tag
- **WHEN** a body contains `<%= name %>`
- **THEN** the opening delimiter, expression region, and closing delimiter are highlighted with the configured EJS colors

#### Scenario: Scriptlet and comment tags
- **WHEN** a body contains `<% code %>` and `<%# comment %>`
- **THEN** both tags are recognized and highlighted; the comment tag uses a comment color

#### Scenario: Broken tag is still delimited
- **WHEN** an EJS tag is not closed in the body
- **THEN** the unterminated opening delimiter is highlighted as an error-colored construct (full validation is owned by the validation capability)

### Requirement: Frontmatter structure highlighting
The plugin SHALL highlight the YAML frontmatter block (delimited by matching top-level `---` lines) as a distinct region, so that frontmatter keys and values are visually separated from the body.

#### Scenario: Frontmatter delimiters
- **WHEN** a template starts with `---`, contains YAML, and is closed by a matching `---` line
- **THEN** the block between the delimiters is highlighted as frontmatter and the delimiters themselves are distinct

#### Scenario: Both layers are EJS-renderable
- **WHEN** the frontmatter values or the `to:` key contain EJS expressions such as `to: app/<%= name %>.js`
- **THEN** the EJS tags inside the frontmatter are highlighted with EJS colors, not YAML colors
