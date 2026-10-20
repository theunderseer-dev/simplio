# Durable Firestore uploads

This stage adds uploads to the durable Room outbox. It does not yet implement remote downloads,
conflict resolution, or offline authentication restoration. Local reads and writes remain available
while an authenticated desktop session is open. Local launch mode never starts Firebase or the uploader.

## Ownership and lifecycle

The desktop database is selected by Firebase UID. `RoomUploadStore` additionally binds its upload
checkpoint to the Firebase project and UID. `FirestoreRestClient` validates every document path and
checks the session owner before and after HTTP requests. Tokens are obtained from
`FirebaseSessionManager` at request time and are never persisted in an upload batch.

`DesktopAttendanceSession` owns the coordinator. Its coroutine is cancelled and joined before Room is
closed. The Firebase HTTP client belongs to the outer authentication container.

## Local transaction and immutable request

A business write and its outbox revision are committed in one Room transaction. Before network access,
`RoomUploadStore.prepare` freezes a snapshot and its exact JSON commit body in `sync_upload_batch`.
Later local edits coalesce in `sync_outbox` but cannot change this prepared request.

The version 7 to 8 migration preserves cloud IDs, mutation IDs and pending revisions. It adds upload
checkpoint/batch tables and aggregate boundaries. Deleted version 7 records whose business parents
are already gone are conservatively grouped into `legacy-deletions`.

The uploader sends catalog changes first, followed by all pending changes for a student, their
purchased packages and attendance. A complete student aggregate is never split. The local limits are
400 business documents, 900,000 encoded bytes per fields payload and 8 MiB per request. Oversized
changes remain local and pause uploads. These limits deliberately leave room below Firestore limits.

## Remote document layout

All documents are beneath `users/{uid}`. The parent document does not need to exist.

| Collection | Contents |
| --- | --- |
| `studentGroups` | account-owned groups |
| `packageTypes` | account-owned package catalog |
| `students` | student name, group cloud ID and note |
| `studentPackages` | purchased terms, student/catalog cloud IDs and purchase date |
| `attendance` | package cloud ID, class date and visit count |
| `syncState/attendance` | account upload head and protocol version |
| `syncReceipts/{batchId}` | immutable batch ID and ordered mutation IDs |

Each business document contains `schemaVersion`, `isDeleted` and `lastMutationId`. Deleted records are
written as tombstones. Activation, expiration and visit totals are derived from attendance and are not
uploaded as additional competing state. Purchased package terms are preserved independently of later
catalog edits. Integer local database IDs never serve as cross-device references.

## Atomic commit and concurrency

`FirestoreAttendanceUploadRemote` uses `documents:commit`, not `batchWrite`. A commit contains all
business writes, a head update and an immutable receipt. Every write has either an `exists: false`
precondition or an exact server `updateTime` precondition.

The account head is a conservative compare-and-set boundary. Another device with a stale or unknown
head cannot overwrite current cloud data. This first upload stage deliberately pauses such a device;
it must not adopt a cloud head without downloading and reconciling the corresponding data. The next
stage must add that pull/reconciliation path before advertising multi-device synchronization.

## Lost response recovery

Before sending, and again after a precondition conflict, the uploader reads the receipt. If absent,
it can send the frozen request. If present, it verifies the complete receipt, matching head batch ID
and exact business fields. It reads each document's actual `updateTime` rather than assuming every
write has the receipt timestamp.

If the head or documents advanced, recovery pauses with a conflict. It never overwrites newer cloud
state or pretends that a stale receipt represents the current head.

After a confirmed commit, one Room transaction acknowledges the frozen revisions, stores the head
version and removes the batch. A newer local edit remains pending with its new mutation ID and the
acknowledged server base. Failure midway through this transaction rolls everything back.

## Retry and user feedback

Transient network errors and HTTP 408/429/502/503/504 retry with exponential delay starting at one
second, capped at 60 seconds plus up to 20 percent jitter. A restart resumes the persisted request.
HTTP 500 or malformed success responses receive one automatic retry before pausing. Authentication,
permission, validation, oversized payload, target mismatch and conflict states remain distinguishable.
Cancellation propagates and never acknowledges pending work.

The desktop shows a persistent upload warning while retrying or paused. Successful background uploads
are silent. The warning says that changes are saved locally; a network failure is not treated as proof
that the operating system has no internet connection. Manual retry is available for recoverable
paused errors. Conflict and ownership mismatch require reconciliation rather than a blind retry.

## Verification

Tests cover immutable requests, cloud references, catalog ordering, complete aggregate boundaries,
newer edits during upload, deletion during upload, account/project mismatch, payload limits, retries,
authentication errors, cancellation and background wake-up on Room changes.

Desktop tests reopen the real database, validate migration 7 to 8, and inject a checkpoint write failure
to verify transactional rollback. Firestore Emulator tests verify actual atomic rollback on a failed
document precondition and head conflicts between independent local databases. Set
`FIRESTORE_EMULATOR_HOST=127.0.0.1:8187` to include them; otherwise those integration tests are skipped.
They use a demo project and emulator owner credentials. They do not validate production Security Rules.

## Remaining work

- remote pull with persisted cursor/checkpoint and merging into observable local data
- user-visible conflict reconciliation and domain-aware handling of concurrent attendance/renewal
- restoring an existing account offline without requiring online sign-in
- explicit connectivity/last-sync UI when both upload and download are implemented
- production Rules tightening and Rules tests before allowing additional accounts
- retention policy for receipts and tombstones after the pull protocol defines safe cleanup

Production Firestore Rules and data are not modified by building or running the automated tests.
Normal authenticated application launch does upload to its configured project; use `--local` when
manual testing should remain local.

## References

- [Firestore commit REST API](https://firebase.google.com/docs/firestore/reference/rest/v1/projects.databases.documents/commit)
- [Write preconditions](https://firebase.google.com/docs/firestore/reference/rest/v1/Precondition)
- [Firestore document updateTime](https://firebase.google.com/docs/firestore/reference/rest/v1/projects.databases.documents)
- [Firestore REST authentication](https://firebase.google.com/docs/firestore/use-rest-api)
- [Offline-first data layer](https://developer.android.com/topic/architecture/data-layer/offline-first)
