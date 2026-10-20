# Firestore downloads into Room

This stage adds remote reads to the existing durable upload protocol. Room remains the source read by
attendance UI and the destination for local edits. Firestore serializes accepted account changes.
The same account can move between devices when the receiving device has no pending user changes.
Independent local and remote changes pause safely; the next layer provides
[explicit conflict review](sync-conflicts.md).

## One account, one runner

`DesktopAttendanceSession` owns `AttendanceSyncCoordinator` and cancels its coroutine before closing
Room. A single cycle mutex serializes recovery, download and upload. Firebase project and UID bind
both the HTTP client and local checkpoint. A session change cancels in-flight work.

A cycle recovers a previously frozen user upload first. Otherwise it reads the cloud head, downloads
when that head changed, applies the result locally, then uploads pending local work. It repeats
immediately while uploads remain. While idle it checks the head every 60 seconds; Room outbox changes
wake it sooner. This is polling while the application is open, not a realtime listener or a scheduler
that runs after the desktop application exits.

## Consistent reads

`FirestoreRestClient.readDocumentSnapshot` uses `batchGet` to obtain the head document and its server
`readTime`. A missing head also supplies a server time. `FirestoreAttendancePullRemote` reads all five
business collections using `runQuery` at exactly that time, ordered by document name with an exclusive
cursor. Each page contains at most 500 documents. This prevents a later commit from mixing new
attendance with old package or student data during one download.

Every response is checked for the bound project, account and collection, timestamp consistency and
duplicate document IDs. There is no fallback to unpinned reads if a historical query fails. Snapshots
are limited to 50,000 business documents and 32 MiB of encoded fields. A failed page never advances the
local checkpoint. A retry starts from a fresh head and read time.

An unchanged known head skips collection reads. The checkpoint records the last successful server
read time; it is not a user-facing promise that an offline device has the newest data. Current data
size makes a full snapshot on head changes preferable to adding a second server change-log protocol.
The page cursor is transient; only the completed snapshot checkpoint is persisted.

## Replacement safeguards

`RoomPullStore` checks eligibility before downloading and again inside the import write transaction.
It accepts replacement only when the database has no pending revisions or frozen upload, or contains
exactly the complete untouched default catalog and groups. The latter are disposable bootstrap data,
not a reason to reject a new device connecting to an existing account.

An old frozen seed-only request can also be discarded, but only if its exact mutation IDs, project,
UID and empty head match those untouched defaults. A batch containing user work is never discarded.
If it might already have reached Firestore, existing receipt recovery runs first.

A missing cloud head never clears local data. Losing a previously known head pauses as a conflict.
An established head plus pending local edits also pauses when the remote head differs, even if the
edits appear unrelated. There is no silent last-writer-wins policy. The UI reports that local and
cloud changes need review. Both sides remain available for the explicit conflict review flow; see
[synchronization conflict review](sync-conflicts.md).

## Atomic import and IDs

`CloudSnapshotMapper` validates the complete graph before business writes: schema versions, fields,
references to live parents, dates, package terms and attendance visit counts. Acknowledged documents
must remain present, including tombstones; a missing document is not interpreted as a deletion.

Cloud IDs map to local integer IDs. Existing mappings survive refreshes. New mappings reserve IDs for
both live rows and tombstones. The import advances SQLite ID sequences past tombstone IDs as well,
so a later registration cannot accidentally reuse a deleted object's sync identity.

One Room transaction replaces business rows in foreign-key order, stores acknowledged sync records,
removes only eligible bootstrap work and saves the head plus read-time checkpoint. Failure rolls the
whole transaction back. Imports bypass the business journal, so downloaded changes do not create
uploads. Purchase dates and purchased terms are preserved. Activation and expiration are derived
from remaining attendance; removing the last visit on another device returns a package to 0 visits
with no activation or expiration date.

Migration 8 to 9 adds nullable `lastPullReadTime` to `sync_upload_state`. Historical schemas are
unchanged. Existing batches and revisions remain recoverable. After an account's first successful
remote check, catalog seeders no longer recreate deleted or customized defaults on startup. Catalogs
and groups belong to that account.

## Observable local UI

`AttendanceModule` provides a Room invalidation flow with an initial emission for the five business
tables. `AttendanceViewModel` reloads attendance, catalogs, groups and class history when those local
tables change. Graph reads run in Room transactions so a read cannot straddle a full replacement.
Successful background synchronization is silent. Existing persistent retry/pause feedback is retained
for connectivity, service, authentication, permissions and conflict failures.

## Verification and limits

`commonTest` covers import validation, dirty-state races, checkpoint monotonicity, unactivated
packages, tombstone IDs, cancellation, retry, idle polling and ViewModel updates. `desktopTest` covers
migration 8 to 9, database reopening, rollback and preventing default catalog resurrection.
`FirestorePullEmulatorTest` verifies actual exchange between two independent Room databases,
historical reads during concurrent writes, and a 501-document collection crossing a page boundary.

Set `FIRESTORE_EMULATOR_HOST=127.0.0.1:8187` to include emulator tests; without it they are skipped.
The demo-project fixture uses emulator owner credentials and does not test production Security Rules.
No Rules are deployed by these tests.

Explicit student-version choices are described in [conflict review](sync-conflicts.md). Remaining work
includes finer-grained merge policies, offline sign-in restoration, last-sync UX and safe
archive/tombstone/receipt cleanup.
A legacy local database containing real pending work cannot be automatically merged with an already
populated cloud account. Cloud documents must be written through the head/tombstone protocol;
manual edits that bypass it are unsupported.

## References

- [Android offline-first data layer](https://developer.android.com/topic/architecture/data-layer/offline-first)
- [Firestore batchGet](https://firebase.google.com/docs/firestore/reference/rest/v1/projects.databases.documents/batchGet)
- [Firestore runQuery](https://firebase.google.com/docs/firestore/reference/rest/v1/projects.databases.documents/runQuery)
- [StructuredQuery and cursors](https://firebase.google.com/docs/firestore/reference/rest/v1/StructuredQuery)
- [Room invalidation flows](https://developer.android.com/reference/androidx/room/InvalidationTracker)
