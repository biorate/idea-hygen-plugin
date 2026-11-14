# Design

## Context

Конфликт из эксплорации: dotenv (bundled, `patterns=".env.*"`) побеждает наш runtime `associatePattern("*.t")` для файла `.env.t`; DotEnv-инспекции анализируют шаблон. Наш lifecycle синка ассоциаций (`HygenTemplatePatternsLifecycle`) уже запускается при старте и на смене настроек — естественная точка детекции. Публичная проверка «кто получит файл» — `FileTypeManager.getFileTypeByFileName()`.

## Goals / Non-Goals

**Goals:**
- Детекция конфликтов на каждом sync-цикле; один агрегированный notification; интерактивное разрешение с явным подтверждением; suppress-память.
- Полная graceful degradation: если правка чужих паттернов недоступна — инструкция, никогда не падение и не «тихий игнор».

**Non-Goals:**
- Автоматическое удаление чужих паттернов (запрещено решением из эксплорации).
- Гарантированная поддержка правки чужих типов на будущих платформах (проверка capabilities в рантайме).
- Разрешение конфликтов, где наш тип не участвует (Perl vs dotenv и т.п.).

## Decisions

1. **Детекция — через публичный API:** для каждого нашего паттерна берём представительное имя (заменяя `*` на пробу: `*.t` → `probe.t`; `*.ejs.t` → `probe.ejs.t`) и сравниваем `getFileTypeByFileName(probe)` с нашим типом. Никаких допущений о порядке регистрации. Представительное имя кэшируется в тестах (чистая функция `representativeName(pattern)`).
2. **Разрешение — двухуровневое.** Tier 1 (preferred): рефлексивная попытка редактирования чужого `FileType` через публичные точки, где они есть (`StructuralSearch`-нет; на 252 доступен внутренний `FileTypeManagerImpl.removeFileSpec` — используется только через try/catch с проверкой наличия метода). Tier 2 (fallback): notification с точной инструкцией (Settings → Editor → File Types → <тип> → удалить паттерн). Оба уровня никогда не выполняются без подтверждения из диалога notification.
3. **Suppress — persistent state:** `PersistentStateComponent` (application-level, рядом с `HygenTemplateSettings`), ключ — имя file type-соперника. Notification API (`NotificationGroup`, balloon) с действием «Don't show for <type>».
4. **Один notification:** все конфликты в одном балуне со списком; actions на каждый конфликт не плодим — список + одно действие «Resolve…», открывающее диалог выбора (или инструкции).
5. **Точка интеграции:** хвост `HygenTemplatePatternsLifecycle` после успешного sync + `ProjectActivity`/startup не нужен отдельно (sync и так идёт на старте). Тесты детектора — чистые (representative name, сравнение типов) + платформенный тест на notification-логику с фейковым FileTypeManager (seam, как в тестах ассоциаций).

## Risks / Trade-offs

- Internal API (`removeFileSpec`) может отсутствовать/измениться — tier 2 покрывает; проверка рефлексией, никаких hard calls.
- Диалог может показаться навязчивым при конфликте с популярными типами (Perl) —suppress и агрегация смягчают.
- Детектор может пропустить конфликт, если платформа разрешает имена контентно (не по имени) — вне охвата, осознанное ограничение.
- Откат безопасен: фича аддитивна, отключается снятием регистрации notification group + хука в lifecycle.
