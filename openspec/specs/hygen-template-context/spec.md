# hygen-template-context Specification

## Purpose
Defines the template-context model: which variables and helpers a hygen template can reference, where that knowledge comes from, and how it feeds identifier completion inside EJS islands.

## Requirements

### Requirement: Engine-default variables are always available
The plugin SHALL treat the engine defaults from the fork's `context.ts` as always visible in any template: `name: 'unnamed'` plus the derived variants the engine creates for `name` (`Name`, `names`, `Names`), regardless of any config file.

#### Scenario: Bare template without config
- **WHEN** a template under `_templates/**` is opened and the project has no `.hygen.js`
- **THEN** completion inside an EJS island offers `name`, `Name`, `names`, `Names` (and `h`)

### Requirement: Variables from the nearest .hygen.js
The plugin SHALL locate the nearest `.hygen.js` by walking from the template's action folder up to the filesystem root (matching the engine's `config-resolver`), parse it as JavaScript (without executing it), and expose:
- keys of `localsDefaults` as available variables;
- property names of the `helpers` object literal as members of `h` (functions and getters alike).

#### Scenario: localsDefaults keys offered
- **WHEN** the nearest `.hygen.js` contains `module.exports = { localsDefaults: { scope: 'app' } }`
- **THEN** `scope` is offered in identifier completion inside EJS islands of templates under that config

#### Scenario: project helpers merge into h
- **WHEN** the nearest `.hygen.js` defines `helpers: { root: ..., stringify: ..., platform }` (methods or getters)
- **THEN** `root`, `stringify`, `platform` are offered after `h.` alongside the built-in catalog members, without "unknown symbol" errors in injected JS

#### Scenario: Missing or unparsable config degrades gracefully
- **WHEN** no `.hygen.js` exists or it cannot be parsed as JavaScript
- **THEN** the plugin falls back to engine defaults only and reports no error

### Requirement: Variables from the action folder's prompt hook
The plugin SHALL parse the action folder's prompt hook — first existing of `prompt.js`, `prompt.ts`, `index.js`, `index.ts`, `prompt.cjs` per the engine's order — without executing it, and expose statically extractable question names as available template variables. Dynamically computed names SHALL be silently skipped.

#### Scenario: Question names offered
- **WHEN** the action folder contains `prompt.js` with `module.exports = [{ name: 'module' }, { name: 'client' }]`
- **THEN** `module` and `client` are offered in identifier completion inside that action folder's templates

#### Scenario: Params keys offered
- **WHEN** the hook exports `params({ args, h })` returning an object literal with statically visible keys `locale` and `server`
- **THEN** `locale` and `server` are offered as template variables

#### Scenario: Dynamic names skipped
- **WHEN** the hook builds questions dynamically (`name: variable`)
- **THEN** nothing is extracted from that construct and no error is reported

### Requirement: In-template declarations resolve positionally
The plugin SHALL collect variable declarations and assignments made inside the template's EJS scriptlet/expression islands (`const/let/var X = ...`, bare `X = ...`) and expose them to identifier completion **only at offsets after the declaration** (EJS renders sequentially).

#### Scenario: Declaration visible below its island
- **WHEN** an island at the top of the template contains `APP_NAME = 'demo'` and an island below references `APP_NAME`
- **THEN** `APP_NAME` is offered in completion in the lower island

#### Scenario: No back-propagation
- **WHEN** an island above the declaration references `APP_NAME`
- **THEN** the reference is not resolved from that declaration (no false positive completion)

### Requirement: Merged context without conflicts
The plugin SHALL merge all sources (engine defaults, `.hygen.js` locals, in-template declarations) into one completion set; later sources shadow earlier ones by name, and the merge SHALL be computed without re-parsing unrelated files on every keystroke (caching keyed on file modification is acceptable).

#### Scenario: Shadowing
- **WHEN** `.hygen.js` sets `localsDefaults: { name: 'x' }` and the template declares `name` in an island
- **THEN** completion still offers `name` once, resolved to the nearest valid source
