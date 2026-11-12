# Spec Delta

## ADDED Requirements

### Requirement: Cross-island JavaScript continuity

All EJS islands of one template file SHALL be injected as a single JavaScript document, so that code constructs spanning islands are analyzed as one program: an opening brace/clause in one island and its closing counterpart in another SHALL NOT produce unbalanced-brace errors. Declarations made in one island SHALL be visible to the islands that follow it in the same file.

#### Scenario: Brace pair spanning islands

- **WHEN** a template contains `<% if (CLIENT) { -%>` in one island and `<% } -%>` in a later island
- **THEN** no "missing }" / "unexpected }" error is reported for either island

#### Scenario: Declaration visible in a later island

- **WHEN** one island declares `const limit = 3` and a later island of the same file references `limit`
- **THEN** `limit` resolves with no unknown-symbol error

#### Scenario: Independent files stay separate

- **WHEN** two template files each declare the same identifier
- **THEN** their injected documents do not interfere: no duplicate-declaration or cross-file resolution

#### Scenario: Synthetic prelude is applied once per file

- **WHEN** a template file has several islands referencing `h.changeCase` and context variables
- **THEN** every island resolves them without duplicate-declaration errors and without unknown-symbol errors
