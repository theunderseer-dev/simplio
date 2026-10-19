# Stable cloud identities and a transactional Room outbox

## Scope

This step prepares local data for synchronization. It does not send requests to Firestore,
restore authentication offline, download remote changes, or display connectivity status.
It builds on the account-scoped database selection in `DesktopDatabaseManager`.

Room remains the source of data read by the application. All existing product mutations
and default seeding now also write synchronization metadata in the same Room writer
transaction. A failure in either write rolls back both, including registration's outer
transaction across the student and purchased package repositories.

## Identity

Local integer keys and foreign keys remain unchanged. `sync_records` maps a local key
and entity type to a cloud ID; these IDs are scoped to the selected account's database
and must later be used under that authenticated user's Firestore path.

| Entity | Local key | Cloud ID |
| --- | --- | --- |
| Student group | integer ID as text | fixed ID for built-in Group A/B, otherwise random UUID |
| Package type | integer ID as text | fixed ID for the first matching built-in template, otherwise random UUID |
| Student | integer ID as text | random UUID |
| Purchased package | integer ID as text | random UUID |
| Attendance | `packageId:epochDay` | purchased package cloud ID plus `_epochDay` |

UUIDs use 32 lowercase hexadecimal characters. Built-in IDs are not shared data:
`users/<uid>/...` still separates each account's catalog and groups. The fixed IDs only
prevent separate devices seeding duplicate built-in documents. Existing custom values
are not replaced with templates. Duplicate template rows in an old database keep distinct
identities; only the first matching row receives the built-in identity.

Attendance identifies a package and a date, not a particular tap on a device. Repeating a
check on the same package/date therefore addresses the same eventual remote document.
Concurrent incompatible visit counts still require a conflict policy in the transport.

Activation and expiration are derived from attendance. Recording/removing attendance does
not enqueue a redundant package update solely because `startedOnEpochDay` changed locally.
The remote package representation must follow this rule and recompute activation on pull.

## Local changes and acknowledgements

`sync_records` retains identity, local revision, acknowledged revision, last confirmed
server version, and a deletion flag. These records survive deletion of the business row.

`sync_outbox` keeps the latest pending state per entity. Each changed revision gets a new
mutation ID and queue position. Reading an unchanged pending item preserves its mutation
ID. Repeated note edits coalesce; unchanged notes and duplicate attendance do not create
new work. This is a desired-state queue, not an audit log or a replay of every user action.

Deleting a student records tombstones for the student, their purchased packages, and all
remaining attendance before Room applies foreign-key cascades. Acknowledgement removes
only the matching queue ID, revision, and mutation ID. An old successful acknowledgement
can advance the confirmed server base while a newer local revision remains queued.
An acknowledgement for an older revision cannot regress a newer confirmed server version.
Tombstones are retained after acknowledgement; retention/cleanup is a later concern.

`revision` is local to this database, not a cross-device timestamp. `serverUpdateTime` is
reserved for the exact Firestore document update time returned by a future upload. It
must not be synthesized from a device clock.

## Write boundary

`RoomStudentRepository`, `RoomStudentPackageRepository`, `StudentGroupSeeder`, and
`PackageTypeSeeder` accept `AttendanceDatabase`, so their business writes and journal
writes cannot accidentally use different transaction runners. `RoomSyncJournal` can
only be created through its transactional `write` function.

Raw DAOs deliberately do not enqueue changes. They are low-level persistence primitives:
tests may use them for setup, and a future remote pull will need to apply server state
without producing another local upload. New user-facing write paths must use a tracked
repository and add matching journal handling. Currently groups/catalog have no custom
editing UI; their product write path is the default seeder.

## Migration

`AttendanceMigration6To7` adds the two sync tables and indexes, assigns identities to all
existing groups, catalog entries, students, purchased packages, and attendance, and queues
an initial upsert for every row. Business rows and schemas 1–6 are unchanged. The template
values in this historical migration are frozen; it must not depend on future changes to
`PackagePlan`. Schema 7 is exported by Room/KSP.

Opening a database alone is sufficient to migrate existing rows. Fresh databases receive
identities through seeding and the tracked repositories. Nothing leaves the device at
this stage, including data in `--local` mode.

## Requirements for the next transport step

The queue is intentionally not presented as a complete sync engine. Before enabling a
sender, implement and test all of the following:

- Read a coherent entity payload, related cloud IDs, revision, and server precondition
  from Room in one consistent snapshot. `PendingSyncChange` alone is not that payload.
- Persist the immutable in-flight request, its mutation IDs, and its recovery state
  before the network call. Coalescing must not erase the ability to recover a request
  that committed remotely just before a crash, especially after a newer local edit.
- Use one uploader per selected account and recover in-flight work before sending newer
  versions of the same records. Do not issue concurrent uploads of successive revisions.
- Resolve dependencies and preserve aggregate invariants. Queue position is not a valid
  dependency order: coalescing repositions records, and migration order is arbitrary.
  Creating a student and purchased package must not expose a half-created registration
  to another device. Apply related writes and related remote pulls atomically.
- Acknowledge only a successfully committed mutation, using the actual server update
  time. A timeout does not prove a write failed. Use server-side mutation deduplication
  and document preconditions, and distinguish conflicts from retryable network errors.
- Preserve pending local changes during pull, merge compatible changes, and surface
  incompatible edits and renewals with domain-specific resolution.

These requirements belong to the Firestore upload/download PR. They are not implemented
by simply polling `getPending()` and sending each row independently.

## Validation

- `RoomSyncOutboxTest` (`commonTest`): related identities, no-activation registration,
  coalescing/no-op edits, duplicate attendance, stale acknowledgements, cascade tombstones,
  attendance reactivation, cross-device default IDs, and pending-count flow.
- `RoomSyncOutboxPersistenceTest` (`desktopTest`): reopen persistence and an injected SQLite
  outbox failure rolling back the full registration, including nested repository writes.
- `AttendanceMigration6To7Test` (`desktopTest`): schema validation, preserved business data,
  custom and duplicate catalog entries, identity backfill, and persistence after reopen.
