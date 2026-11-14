# Tasks

## 1. Детектор конфликтов

- [x] 1.1 Чистый `representativeName(pattern)` (`*.t` → `probe.t`, `*.ejs.t` → `probe.ejs.t`, инвалиды → null) + тесты.
- [x] 1.2 `FileTypeConflictDetector`: для каждого применённого паттерна сравнить `getFileTypeByFileName(representative)` с hygen-типом; собрать список конфликтов (pattern, чужой тип, пример имени); публичный seam для подмены FileTypeManager в тестах; тесты: конфликт есть/нет, несколько паттернов, suppress-игнорирование.

## 2. Notification и разрешение

- [x] 2.1 Notification group в `plugin.xml`; агрегированный balloon со списком конфликтов; suppress-действие «Don't show for <type>» → `PersistentStateComponent` (application-level); тесты на state.
- [x] 2.2 Действие разрешения: tier 1 — рефлексивная правка чужого file type (только конфликтный паттерн, только после подтверждения в диалоге), tier 2 — инструкция (Settings → Editor → File Types); после успешного tier 1 — повторный sync-цикл; тесты tier 1/tier 2 на seam'ах.

## 3. Интеграция и выпуск

- [x] 3.1 Хук детектора в `HygenTemplatePatternsLifecycle` после sync; регистрация state/notification в `plugin.xml`; `./gradlew test --console=plain` — все зелёные (известный экологический red `HygenRealWorldTemplatesTest` фиксируется отдельным чейнджем каталога ключей — не блокирует, если остаётся единственным падением).
- [x] 3.2 Ручная проверка в IDEA 2025.2 с установленным dotenv: notification при старте (подтверждена — balloon с конфликтами `.env file`/Jenkins/Jenkinsfile), действие возвращает `.env.t` hygen-типу (разрешение проверено вручную через UI File Types точным именем, а не кнопкой Resolve — вытеснено следующим чейнджем claim-back-conflicting-files), suppress работает (проверка suppress отложена до claim-back чейнджа).
- [x] 3.3 Версия 0.6.0, `buildPlugin`, установка, README EN/RU (раздел «Conflicting file types»), VERIFICATION.md.
