# hygen-completion Specification

## Purpose
Defines autocompletion inside hygen templates: frontmatter keys with fork semantics and members of the `h` helper object.

## Requirements

### Requirement: Frontmatter key completion
Inside the frontmatter block, the plugin SHALL offer completion of the known frontmatter keys (`to`, `inject`, `sh`, `before`, `after`, `prepend`, `append`, `at_line`, `skip_if`, `skip_if_not`, `create_if_missing`, `replace`, `unless_exists`, `local`) with short descriptions reflecting fork behavior.

#### Scenario: Key completion in frontmatter
- **WHEN** the user invokes completion on an empty frontmatter line
- **THEN** the known frontmatter keys are offered, each with a one-line description

#### Scenario: Fork-only keys are included
- **WHEN** completion is invoked in frontmatter
- **THEN** fork-specific keys such as `create_if_missing`, `replace`, and `skip_if_not` are offered alongside upstream keys

### Requirement: Helper object completion
Inside EJS expressions, after the prefix `h.`, the plugin SHALL offer completion of the helper namespaces `changeCase` and `inflection`, and after `h.changeCase.` and `h.inflection.` the members of change-case 3.x and inflection 1.x respectively (for example `h.changeCase.pascalCase`, `h.inflection.pluralize`), plus built-ins such as `h.capitalize`.

#### Scenario: Helper namespace completion
- **WHEN** the user types `h.` inside an EJS expression and invokes completion
- **THEN** `changeCase`, `inflection`, and built-in top-level helpers such as `capitalize` are offered

#### Scenario: Nested helper member completion
- **WHEN** the user types `h.changeCase.` and invokes completion
- **THEN** change-case 3.x function names are offered

### Requirement: Completion degrades gracefully
The plugin SHALL NOT suppress other completion providers or produce errors when the template is syntactically incomplete; completion SHALL remain available on partially broken templates.

#### Scenario: Completion in a broken template
- **WHEN** the body contains an unterminated EJS tag and the user invokes completion after `h.`
- **THEN** helper completion is still offered and no error dialog appears
