# Spec Delta

## Purpose

Defines JavaScript language injection into EJS islands and the synthetic declarations that make injected code resolve (no false "unknown symbol" errors), with explicit degradation rules.

## ADDED Requirements

### Requirement: JavaScript injection into EJS islands
The plugin SHALL inject JavaScript into the code regions of EJS islands: `<% ... %>` (scriptlet), `<%= ... %>` (expression), and their trimming variants (`<%-`, `<%_`, `_%>`, `-%>`). Frontmatter and body text SHALL NOT be injected. Injected code SHALL receive standard JavaScript highlighting, error highlighting, and completion.

#### Scenario: Expression gets JS features
- **WHEN** a template contains `<%= h.changeCase.param(name) %>`
- **THEN** the island body is highlighted as JavaScript, supports Ctrl+Space completion, and shows JS syntax errors

#### Scenario: Non-code regions untouched
- **WHEN** a template has frontmatter and a plain-text body
- **THEN** frontmatter keeps its own highlighting and the body keeps host-language injection from the existing multi-host injector

### Requirement: Synthetic declarations for the template context
The plugin SHALL provide synthetic declarations inside injected JS for: the template-context variables (per the `hygen-template-context` capability) and the `h` object — built-in helper members from the existing catalogs (`changeCase`, `inflection`, `capitalize`, `path`) merged with `helpers` from the nearest `.hygen.js`. Helper members whose nature is known SHALL be typed as callable; unknown value types SHALL fall back to `any`-like treatment rather than error markers.

#### Scenario: h.changeCase chain resolves
- **WHEN** the template contains `<%= h.changeCase.param(name) %>`
- **THEN** `h`, `h.changeCase`, and `param` resolve with no error highlighting

#### Scenario: Project helper resolves
- **WHEN** `.hygen.js` defines helper `root` and the template contains `<%= root('${ROOT}/x') %>`
- **THEN** `root` resolves with no error highlighting

### Requirement: Graceful degradation
The plugin SHALL NOT throw, blank the editor, or produce cascading error markers on unterminated EJS tags, empty islands, unparsable JS fragments, or missing context sources. An unterminated island MAY lose injection; the rest of the file SHALL keep template highlighting (consistent with `hygen-validation`'s unterminated-tag inspection still firing).

#### Scenario: Unterminated tag
- **WHEN** a template contains `<%= h.changeCase.param(name` with no closing tag
- **THEN** the editor stays open and usable, the unterminated-tag inspection fires, and no plugin exception is logged
