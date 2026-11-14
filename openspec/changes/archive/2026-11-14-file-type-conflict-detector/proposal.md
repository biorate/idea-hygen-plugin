# Proposal

## Why

Наблюдаемый инцидент: шаблон `_templates/create/server/apps/server/.env.t` захвачен bundled-плагином dotenv, чей file type регистрирует паттерн `.env.*`; платформа отдаёт файл DotEnv-языку, и пользователю показываются чужие предупреждения («Nested variables must not appear outside double quotes»), а подсветка и инспекции hygen в файле отсутствуют. Аналогичные конфликты возможны с любым чужим паттерном (Perl и пр.) — сегодня они не видны ни нам, ни пользователю.

## What Changes

- Новый **детектор file-type конфликтов**: при применении паттернов (на каждом sync-цикле ассоциаций) для каждого нашего шаблонного имени проверяется, какой file type реально его получит (`FileTypeManager.getFileTypeByFileName()` — публичный API). Если это не наш тип — фиксируется конфликт.
- **Notification** с перечнем конфликтов и интерактивным разрешением: пользователь явно выбирает действие (например, «убрать паттерн `.env.*` у типа .env file» — только по подтверждению, никогда не автоматически); если платформа позволяет — действие выполняется через editing чужого file type, иначе показывается пошаговая инструкция (Settings → Editor → File Types).
- **Suppress-состояние** («больше не показывать для этого типа») в persistent state, чтобы не надоедать.
- Допущения: автоснос чужих паттернов запрещён (решение из эксплорации); internal-API shortcut допустим как опциональный путь с graceful fallback на инструкцию.

## Capabilities

### New Capabilities

- `hygen-file-type-conflicts`: обнаружение и разрешение конфликтов между шаблонными паттернами плагина и чужими file types.

### Modified Capabilities

Нет — поведение распознавания (`hygen-template-language`) не меняется, конфликт возникает вне нашего контроля.

## Impact

- Новый пакет `conflicts/` (детектор + notification + action) в `src/main/kotlin/com/biorate/hygen/`.
- Хук в `HygenTemplatePatternsLifecycle` (sync-цикл) + registration в `plugin.xml` (notification group).
- Persistent state для suppress.
- Версия 0.6.0; README EN/RU — раздел о конфликтах file types.
