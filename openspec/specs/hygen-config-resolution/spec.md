# hygen-config-resolution Specification

## Purpose
Defines how the plugin locates and reads the project-level hygen configuration file: the four file names the engine supports, resolved in the engine's order, as the basis for template context (locals defaults and helpers).

## Requirements

### Requirement: Config file discovery follows engine resolution order

The plugin SHALL locate the project config by walking directories from the template's folder up to the filesystem root, checking, within each directory, the engine's candidate names in priority order `.hygen.js`, `.hygen.cjs`, `.hygen.mjs`, `.hygen.ts`; the first existing candidate wins. The nearest config's `localsDefaults` and `helpers` SHALL feed the template context exactly as the single `.hygen.js` does today.

#### Scenario: TS config is picked up

- **WHEN** a project has only `.hygen.ts` (no `.hygen.js/.cjs/.mjs`) above the template with `localsDefaults: { name: 'widget' }`
- **THEN** completion offers `name` with default `widget` in the template's EJS islands

#### Scenario: Priority within one directory

- **WHEN** a single directory contains both `.hygen.js` and `.hygen.ts` with different `localsDefaults`
- **THEN** the plugin reads `.hygen.js` (higher priority) and ignores `.hygen.ts`

#### Scenario: Nearest directory wins

- **WHEN** configs exist both in a subdirectory near the template and in the repository root
- **THEN** the nearer config is used
