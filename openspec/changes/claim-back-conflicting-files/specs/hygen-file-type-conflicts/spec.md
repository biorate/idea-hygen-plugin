# Spec Delta

## MODIFIED Requirements

### Requirement: Interactive conflict resolution

The plugin SHALL notify the user about detected conflicts with a single aggregated notification. The notification SHALL offer an explicit resolution action; no other plugin's patterns SHALL ever be modified automatically or silently. When the conflicting files exist in the project, the primary offered action SHALL be claim-back (adding exact file names to the hygen type, see the `hygen-file-type-claims` capability); removal of the foreign type's conflicting pattern remains available after user confirmation; when the platform does not permit editing the shadowing file type, the notification SHALL show manual steps (Settings → Editor → File Types).

#### Scenario: User removes the shadowing pattern

- **WHEN** a `.env file` conflict notification is shown and the user confirms the pattern-removal action
- **THEN** only the conflicting pattern `.env.*` is removed from the `.env file` type, its other patterns stay intact, and subsequent syncs report no conflict for `.env.t`

#### Scenario: No silent modifications

- **WHEN** a conflict is detected and the user does not act on the notification
- **THEN** no other plugin's file type is modified and no exact names are claimed

#### Scenario: Resolution action is unavailable

- **WHEN** the platform does not allow editing the shadowing file type's patterns
- **THEN** the notification shows manual instructions instead of a failing or silently ignored action
