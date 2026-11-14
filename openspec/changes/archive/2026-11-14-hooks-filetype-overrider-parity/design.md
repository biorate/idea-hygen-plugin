# Design

## Context

`HygenFileTypeOverrider` (single `FileTypeOverrider` for the whole plugin) exempts files from hygen-template override via `HookFiles.NAMES` — currently only `prompt.js`, `index.js`, `prompt.ts`, `index.ts`. The engine's render pipeline excludes a wider hook set (`@biorate/hygen` `src/render.ts` `ignores`): additionally `hooks.ts`, `hooks.js`, `hooks.mjs`, `hooks.cjs`. The gap hijacks `hooks.ts` (and would hijack `hooks.js/mjs/cjs`) under `_templates`, breaking the IDE's TypeScript service for those files (see proposal — Why).

Two hook-name lists already exist in the codebase with different roles:
- `HygenConfigLocator.HOOK_FILE_NAMES` — prompt-hook *resolution order* (which file the engine executes as the prompt hook), used by config/hook lookup. Not the render-exclusion set; `prompt.cjs` is there but `hooks.*` are not.
- `HookFiles.NAMES` — render-exclusion semantics: "files under an action folder the engine does NOT render". Used by the overrider and by `HygenEjsInHookFileInspection` (via `isHookFileAtPath`).

## Goals / Non-Goals

**Goals:**
- Make the overrider's exemption set match the engine's render `ignores` hook names exactly.
- Regression tests locking the parity.
- Fix is minimal, no behavioral risk to `.t` template recognition.

**Non-Goals:**
- No unification of `HygenConfigLocator.HOOK_FILE_NAMES` and `HookFiles.NAMES` into one list — they encode different engine semantics (prompt-hook resolution order vs render ignores); merging them would change unrelated behavior.
- No dynamic reading of the engine's `ignores` from node_modules — fragile and contrary to the plugin's static-parity approach used so far.
- No `pattern`-based exemption for other engine ignore entries (`.hygenignore`, `.DS_Store`, etc.) — out of scope; they are not code files the TS service cares about.

## Decisions

- **D1: Extend `HookFiles.NAMES` in place** with `hooks.ts`, `hooks.js`, `hooks.mjs`, `hooks.cjs`, keeping the four existing names. Single source for the overrider and `HygenEjsInHookFileInspection`; both call sites immediately benefit (the inspection starts actually running on `hooks.ts`, which its `plugin.xml` comment already assumes).
- **D2: Exact-name matching stays.** The engine matches `ignores` with `endsWith`; hook files are conventionally at the action-folder root. Keeping exact-name matching avoids exempting e.g. `my-hooks.ts` template files. (Engine also uses `endsWith`, but templates named `*.hooks.ts` under `_templates` are not a real corpus case; note as a known deviation, no guarantee.)
- **D3: Version 0.7.1** — bugfix, no new capability surface beyond the corrected recognition rule already specced.

## Risks / Trade-offs

- A real template (rendered file) named exactly `hooks.ts`/`index.ts` under `_templates` would now be highlighted as TypeScript instead of a template. The engine wouldn't render such a file anyway (it ignores it), so the plugin now matches engine semantics — correct by definition.
- Upstream may extend `ignores` again; parity is manual (same as existing `HygenConfigLocator` lists). Covered by the batch-glob catalog change deferred earlier.
