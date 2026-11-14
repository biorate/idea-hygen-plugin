# hygen-file-type-claims Specification

## Purpose

TBD - created by syncing change 2026-11-14-claim-back-conflicting-files. Update or remove this section.

## Requirements

### Requirement: Conflict claim-back via exact file names

When a conflict is detected between a configured template pattern and a foreign file type's pattern, the plugin SHALL offer to claim back the conflicting files by adding their exact file names (e.g. `.env.t`) as file name associations of the hygen template type. Claimed exact names take precedence over the foreign type's glob patterns through the platform's standard file name resolution, and the foreign type's patterns remain unchanged.

#### Scenario: User claims back a conflicting file

- **WHEN** a conflict notification lists `.env.t` (claimed by the `.env file` type) and the user confirms the claim-back action
- **THEN** the exact name `.env.t` is added as a file name association of the hygen template type, `.env.t` resolves to the hygen type, and the foreign type's patterns are unchanged

### Requirement: Claim-back limited to real project files

Only exact file names of files actually present in the project SHALL be offered and claimed. When a foreign pattern intersects a configured pattern but no files matching both patterns exist in the project, the plugin SHALL NOT claim any name for that intersection and SHALL NOT offer claim-back for it.

#### Scenario: Only real files are claimed

- **WHEN** a foreign pattern `.env.*` intersects the configured pattern `*.t` but no `.env.t`-like files exist in the project
- **THEN** no file name is claimed and the notification does not offer claim-back for that conflict

### Requirement: Claim lifecycle management

The plugin SHALL persist the exact file names it has claimed. On every sync cycle the plugin SHALL remove its claimed file name associations whose underlying conflict no longer exists (the configured pattern no longer produces the intersection, or the file no longer exists). Claimed names SHALL survive IDE restarts while their conflict persists.

#### Scenario: Claim is removed when the pattern disappears

- **WHEN** the user removes pattern `*.t` from the plugin settings and a sync runs
- **THEN** the plugin's exact-name association for `.env.t` is removed

#### Scenario: Claims survive restarts while the conflict persists

- **WHEN** the IDE restarts and the same conflict (pattern `*.t` vs foreign `.env.*` with file `.env.t` present) is detected
- **THEN** the persisted claim stays in place and the file still resolves to the hygen type

### Requirement: Claim-back is the preferred resolution offer

The conflict notification SHALL offer claim-back as the primary resolution action for conflicts that have existing project files, with the foreign-pattern removal (Resolve) and per-type suppression remaining available. The notification SHALL NOT re-report a conflict whose conflicting file names already resolve to the hygen template type through exact-name associations.

#### Scenario: Claimed conflict is not re-reported

- **WHEN** the file `.env.t` resolves to the hygen template type via a persisted exact-name claim and a sync runs
- **THEN** no conflict is reported for `.env.t` or its pattern pair

#### Scenario: Both resolution paths are offered

- **WHEN** a conflict notification is shown with existing project files under the intersection
- **THEN** the notification offers claim-back (primary), foreign-pattern removal, and suppression actions
