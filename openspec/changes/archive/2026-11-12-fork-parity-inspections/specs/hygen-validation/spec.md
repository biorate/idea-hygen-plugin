# Spec Delta

## ADDED Requirements

### Requirement: Setup attribute value validation

The plugin SHALL flag a `setup:` frontmatter value that does not contain `/` as a warning, because the engine throws `SetupRepositoryError` for such values at generation time. The message SHALL reference the `owner/repo` shape. Values containing `/` and absent values SHALL NOT be flagged.

#### Scenario: Setup without slash

- **WHEN** a template's frontmatter contains `setup: myrepo` (no `/`)
- **THEN** a warning is reported on the value; the text mentions that the engine requires an `owner/repo` value

#### Scenario: Valid setup is not flagged

- **WHEN** a template's frontmatter contains `setup: biorate/core-templates`
- **THEN** no warning is reported

#### Scenario: Dynamic setup value is not flagged

- **WHEN** the `setup:` value contains an EJS island (e.g. `setup: <%= name %>`)
- **THEN** no warning is reported, because the rendered value is not statically known

### Requirement: From attribute file existence

The plugin SHALL flag a `from:` frontmatter value whose referenced file does not exist relative to the nearest `_templates` directory found walking up from the template file. When no `_templates` directory can be found above the template, the inspection SHALL NOT report anything (no false positives). The message SHALL show the resolved path that was not found.

#### Scenario: From points to missing file

- **WHEN** a template has `from: snippets/header.txt` and `_templates/snippets/header.txt` does not exist
- **THEN** a warning is reported naming the resolved path `_templates/snippets/header.txt`

#### Scenario: From with existing file is not flagged

- **WHEN** the referenced file exists under the nearest `_templates` directory
- **THEN** no warning is reported

#### Scenario: No templates directory found

- **WHEN** walking up from the template file finds no `_templates` directory
- **THEN** nothing is reported, regardless of the `from:` value
