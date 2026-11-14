# Proposal

## Why

`HygenFileTypeOverrider` recognizes every file under a project-local `_templates` directory as a hygen template, except a hard-coded hook-file list. That list (`prompt.js`, `index.js`, `prompt.ts`, `index.ts`) is stale relative to the engine: `@biorate/hygen` `src/render.ts` `ignores` also contains `hooks.ts`, `hooks.js`, `hooks.mjs`, `hooks.cjs` (upstream commit `96eadd896` introduced `hooks.ts` for the interactive picker). As a result, the plugin's overrider hijacks real TypeScript hook files such as `_templates/create/*/hooks.ts` in `/www/biorate/application-template`: the file resolves to the Hygen Template file type instead of TypeScript, so the IDE's TypeScript service never processes it. The user sees two false warnings on every such file: "Corresponding file is not included in tsconfig.json" and "Unresolved variable stdin". Disabling the plugin removes the warnings (verified 2026-11-14); tsconfig, `tsc --noEmit` and tsserver are all healthy, so the plugin is the confirmed cause.

## What Changes

- Extend `HookFiles.NAMES` to the full engine ignore set of hook names: add `hooks.ts`, `hooks.js`, `hooks.mjs`, `hooks.cjs` to the existing `prompt.js`, `prompt.ts`, `index.js`, `index.ts`, so the overrider leaves all engine-executed hook modules to their ordinary JS/TS file types.
- Add regression tests: a `hooks.ts` (and `hooks.js`/`hooks.mjs`) file under `_templates` is NOT overridden to the Hygen Template type, while a non-hook file under `_templates` still is.
- Bump plugin version to 0.7.1.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `hygen-template-language`: the "Template file type recognition" requirement gains the hook-file exemption: engine hook files (`hooks.*`, `prompt.*`, `index.*` per the engine's ignore set) under `_templates` SHALL keep their ordinary file type, because the engine executes them, not renders them.

## Impact

- Code: `src/main/kotlin/com/biorate/hygen/lang/HookFiles.kt` (NAMES set only).
- Tests: `src/test/kotlin/com/biorate/hygen/lang/HygenFileTypeOverriderTest.kt`.
- Users: hook files under `_templates` regain TypeScript analysis; no settings or data migration; no effect on template recognition of `.t` files.
- Version: 0.7.0 → 0.7.1 (bugfix release).
