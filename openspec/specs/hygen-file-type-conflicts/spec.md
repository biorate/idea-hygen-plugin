# hygen-file-type-conflicts Specification

## Purpose

TBD - created by syncing change 2026-11-14-file-type-conflict-detector. Update or remove this section.

## Requirements

### Requirement: Template file type conflict detection

Whenever the plugin applies its template filename patterns (at startup sync and on every settings change), it SHALL verify, for each configured pattern and representative file names matching it, which file type the platform actually assigns (`FileTypeManager.getFileTypeByFileName()`). If a matching file name resolves to a file type other than the plugin's hygen template type, the plugin SHALL record a conflict listing the pattern, the shadowing file type, and an example file name.

#### Scenario: Bundled plugin shadows a template pattern

- **WHEN** a pattern `*.t` is configured and a file named `.env.t` resolves to the `.env file` type (its bundled pattern `.env.*`)
- **THEN** a conflict is recorded naming the `.env file` type and the `.env.t` example

#### Scenario: No conflict when the plugin type wins

- **WHEN** every configured pattern's representative file names resolve to the hygen template type
- **THEN** no conflict is recorded and no notification is shown

### Requirement: Interactive conflict resolution

The plugin SHALL notify the user about detected conflicts with a single aggregated notification. The notification SHALL offer an explicit resolution action; no other plugin's patterns SHALL ever be modified automatically or silently. When the platform permits editing the shadowing file type, the action SHALL remove only the conflicting pattern after user confirmation; otherwise the notification SHALL show manual steps (Settings → Editor → File Types).

#### Scenario: User removes the shadowing pattern

- **WHEN** a `.env file` conflict notification is shown and the user confirms the removal action
- **THEN** only the conflicting pattern `.env.*` is removed from the `.env file` type, its other patterns stay intact, and subsequent syncs report no conflict for `.env.t`

#### Scenario: No silent modifications

- **WHEN** a conflict is detected and the user does not act on the notification
- **THEN** no other plugin's file type is modified

#### Scenario: Resolution action is unavailable

- **WHEN** the platform does not allow editing the shadowing file type's patterns
- **THEN** the notification shows manual instructions instead of a failing or silently ignored action

### Requirement: Conflict notification suppression

The user SHALL be able to suppress conflict notifications for a given shadowing file type. The choice SHALL persist across IDE restarts, and suppressed conflicts SHALL not produce notifications on subsequent syncs.

#### Scenario: Suppressed conflicts stay silent

- **WHEN** the user suppressed notifications for a shadowing file type and that conflict re-appears on a later sync
- **THEN** no notification is shown for it
