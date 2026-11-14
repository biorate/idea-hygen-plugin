# Tasks

## 1. Hook-name parity fix

- [x] 1.1 Extend `HookFiles.NAMES` in `src/main/kotlin/com/biorate/hygen/lang/HookFiles.kt` with `hooks.ts`, `hooks.js`, `hooks.mjs`, `hooks.cjs` (full engine render-ignores hook set), update the KDoc to reference `@biorate/hygen` `src/render.ts` `ignores`.
- [x] 1.2 Add regression tests to `HygenFileTypeOverriderTest.kt`: `hooks.ts`, `hooks.js`, `hooks.mjs`, `hooks.cjs` under `_templates/...` are NOT overridden to Hygen Template; `helpers.js` under `_templates/...` still IS; `hooks.ts` outside `_templates` is unaffected.
- [x] 1.3 Verify `HygenEjsInHookFileInspection` behaves on `hooks.ts` (now covered by `isHookFileAtPath`): test or manual note that EJS-tag warning applies to hook files per its existing logic, no changes expected to the inspection itself.

## 2. Release housekeeping

- [x] 2.1 Bump version to 0.7.1 in `build.gradle.kts` (and README EN/RU if they mention the version or the hook-file recognition).
- [x] 2.2 Run `./gradlew test --console=plain`; expected green except the known environment-red `HygenRealWorldTemplatesTest` (separate batch-glob change).
- [x] 2.3 Manual verification in IDEA 2025.2 with `/www/biorate/application-template`: rebuild + install plugin, confirm `hooks.ts` files under `_templates/create/*/` show no "Corresponding file is not included in tsconfig.json" and no "Unresolved variable stdin"; confirm `.t` templates still open as hygen templates. Record the checklist in VERIFICATION.md. (Выполнено 2026-11-14: jar 0.7.1 установлен, пользователь подтвердил после рестарта — предупреждения на `hooks.ts` исчезли, `.t`-шаблоны работают.)
