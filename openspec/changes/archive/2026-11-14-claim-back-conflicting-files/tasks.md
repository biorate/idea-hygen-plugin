# Tasks

## 1. Спайк и фундамент claim-менеджера

- [x] 1.1 Спайк (выполнен в ходе ручной проверки 0.7.0): exact-имя бьёт чужой glob — подтверждено дважды (вручную через Settings → File Types, затем автоматически через claim-back: `.env.t` перешёл к hygen, паттерны dotenv не тронуты; лог: `claim-back done: claimed 1 name(s) [.env.t]`, `.env.t` резолвится в наш тип). `associatePattern` с точным именем регистрируется корректно. Конкурирующие exact-имена у разных типов не встретились в реальной среде — поведение недокументировано, гарантии не даём (зафиксировано как известное ограничение).
- [x] 1.2 `FileTypeClaimManager`: persistent state (application-level, стиль `HygenFileTypeConflictSettings`), API `claim(name)` / `release(name)` / `claimed()`; снимается только через публичный `removeAssociation`; никогда не трогает чужие exact-имена. Тесты на state и lifecycle (add/remove/persist-форма).

## 2. Поиск конфликтных файлов и детекция с учётом claim'ов

- [x] 2.1 Поиск реальных файлов под пересечением (наш паттерн × чужой паттерн) по content roots открытых проектов через `GlobMatcher`; кап на количество имён (20). Тесты: пересечение есть/нет, кап, файлы вне корней игнорируются.
- [x] 2.2 Интеграция claim'ов в детекцию: конфликт не репортится, если все реальные файлы пары уже резолвятся в наш тип; cleanup claim'ов на каждом sync (файл исчез или паттерн убран → release). Тесты на seam'ах.

## 3. Notification и выбор действия

- [x] 3.1 Действие «Claim back N file(s)…» в балуне для конфликтов с существующими файлами: список с чекбоксами → подтверждение → claim → ре-sync; «Remove foreign pattern» (существующий Resolve) остаётся; suppress остаётся. Тесты контроллера: только подтверждённые имена, повторный показ подавлен для уже claim'нутых пар.
- [x] 3.2 Обновление suppression/спецификации репортера: конфликт, чьи файлы уже claim'нуты, не попадает в агрегированный balloon. Тесты.

## 4. Интеграция и выпуск

- [x] 4.1 Хук claim-менеджера в `HygenTemplatePatternsLifecycle` (cleanup до детекции); регистрация state в `plugin.xml`; `./gradlew test --console=plain` — все зелёные (известное окружение-красное `HygenRealWorldTemplatesTest` не блокирует).
- [x] 4.2 Ручная проверка выполнена 2026-11-14 (все пункты подтверждаются логом `idea.log` и наблюдением пользователя): (а) balloon предложил «Claim back 1 file(s)…» для `.env.t` (лог: `claim-back offered: 1 name(s) [.env.t] for pattern *.t`); (б) подтверждение → claim записан и пережил resync (`claim-back done: claimed 1 name(s) [.env.t]`), `.env.t` получил hygen-подсветку, dotenv-предупреждения исчезли, паттерны dotenv не тронуты; (в) claim сохраняется в persist-state (`hygenFileTypeClaims.xml`), конфликт не репортится; (г) автоснятие реализовано cleaner'ом (release при исчезновении файла/паттерна, warn при probe-mismatch). Отзыв claim'а вручную: Settings → Editor → File Types → @biorate/hygen Template → удалить точное имя — cleaner подхватит и снимет из persist-state. По ходу проверки исправлены: постпроектный ре-sync (детекция до открытия проектов), инвертированный предикат остановки сканера, глобальный dedup balloon-сигнатуры (учёт file names), стирание exact-ассоциаций wildcard-sync'ом, EDT-зависание сканера (однопроходный обрезанный обход в фоне).
- [x] 4.3 Версия 0.7.0, `buildPlugin`, установка, README EN/RU (обновить раздел «Conflicting file types»: claim-back как предпочтительный способ), VERIFICATION.md.
