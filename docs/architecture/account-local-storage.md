# Account-scoped local storage

## Context

The desktop app previously opened one Room database and seeded it before Firebase sign-in.
Every authenticated account and the `--local` development mode therefore used the same students,
package catalog, groups, purchased packages and attendance history.

Cloud synchronization needs an explicit local owner before it can safely upload existing data.
Existing local integer primary keys are not global sync identifiers.

## Decision

- Open attendance storage only after sign-in, or when explicitly running in local development mode.
- Give each Firebase UID its own Room database and attendance dependency container.
- Derive account directory names from a SHA-256 digest of the full UID. This prevents path traversal
  and avoids using a raw UID as a path segment. It is not encryption.
- Retain the existing Room schema and all historical exported schemas unchanged.
- Initialize default package types and groups inside the selected database.
- Use a fresh Koin module definition for each attendance container. Singleton factories must not
  be shared between containers.
- Clear the account's ViewModelStore before closing its Koin container and database.
- Run Room initialization off the UI thread and close resources even when initialization is cancelled.

## Existing local data

When an unassigned `simplio.db` exists, signing in does not automatically assign it to that account.
The user chooses **Use existing data** or **Start empty**.

Choosing existing data writes an atomic `legacy-owner` record containing the account digest.
That account continues using the original database in place. No SQLite database, WAL or SHM file
is copied or renamed, so migration does not introduce a partial file-copy state.
The record persists across application restarts and cannot be reassigned by signing in as another UID.
An invalid ownership record blocks opening storage rather than guessing an owner.

Choosing an empty database reserves the account directory and leaves the old database unassigned.
This decision also survives a restart. Importing into an account that has already selected a separate
database is rejected; merging two existing databases is outside this change.

The `--local` mode retains the original development database until it is assigned to an account.
After assignment, local mode uses a separate `local/simplio.db` and never opens the assigned database.
This means local mode starts with an empty student list after account adoption; the old students
remain available when signed in as their selected owner.

A process-wide file lock prevents two instances of this application version from opening storage
while ownership is being selected. The operating system releases the lock if the process exits.
Older application versions do not participate in this lock and must be closed before upgrading.

## Paths

All paths are relative to `~/Library/Application Support/Simplio/`:

| Data | Location |
| --- | --- |
| Existing database, before or after explicit adoption | `simplio.db` |
| Ownership of the existing database | `legacy-owner` |
| New account database | `accounts/<SHA-256 of UID>/simplio.db` |
| Local development database after adoption | `local/simplio.db` |
| Application instance lock | `database.lock` |

The digest and ownership record are local routing metadata, not an authorization mechanism.
Firestore Security Rules must independently enforce the authenticated UID for every cloud request.

## Alternatives

Adding an owner column to every table is also valid, but would require changing all existing keys,
foreign keys and queries at once. A separate database per account preserves the current relational
model and provides a smaller, testable first step for this application.

Automatically assigning all local data to the first account would be simpler but could attribute real
student data to the wrong user. Explicit adoption avoids that ambiguity.

## Verification

- `DesktopDatabaseManagerTest` covers stable paths, explicit adoption, ownership persistence,
  local-mode separation, invalid ownership metadata, path traversal and exclusive access.
- `DesktopAttendanceSessionTest` covers independent Room/Koin instances, persisted students and notes,
  adoption with preserved package and attendance data, and ViewModel cleanup before dependencies close.
- Existing attendance DI tests now request a new attendance module instance.
- Manually check first sign-in with existing data, restart, another account and `--local`.

## Scope

This change prepares local account isolation. It does not upload data, persist Firebase credentials,
add sign-out UI, implement background synchronization or change the published Firestore rules.
The current sign-in gate still requires a successful Firestore access check.

Global entity IDs, a transactional outbox, remote reconciliation and offline session restoration
follow in separate changes. Students, groups, package types, purchased packages and attendance
will all be scoped to the selected account. Class history remains derived from attendance and schedule.
