# Explicit synchronization conflict review

This stage adds a review flow to the existing account-scoped pull/upload protocol. It does not choose
which device wins by a local timestamp. Room remains observable local storage; Firestore update times
are concurrency preconditions, not product decisions about attendance or payments.

## Product decision

The choice boundary is one student together with their name, group, note, all purchased packages and
attendance. A concurrent renewal must not be combined automatically with a check made against an old
package. The review therefore shows complete device and cloud versions, including purchase dates,
package terms, attendance dates/counts, activation and expiration.

The user can keep **This device** for one student and **Cloud** for another. No version is preselected.
**Later** leaves local business data and pending changes untouched. **Apply and sync** explicitly
accepts the decisions. Non-overlapping changes for other students are combined as part of that action.
If there are no overlapping changes, the review offers a single combine action without forced choices.
This stage does not silently run that combine action in the background.

A device choice replaces the complete affected student version. Remote-only purchased packages or
attendance for that student become tombstones, including a package restored after a previous deletion.
The preview matters: choosing an older device version can discard a concurrent cloud renewal from
active data. The archive retains both versions. Field-by-field note/attendance merging and moving an
attendance between purchased packages are separate policies, not implicit behavior of this screen.

Groups and package types are reviewed individually when affected. A local student/package depending
on a remotely deleted catalog entry brings that dependency into review. A combination that would
leave a missing parent is rejected before any local business writes; the user can change the choices.

## Comparison and idempotency

`RoomConflictStore` captures local records, current business fields, pending revisions and any frozen
upload request in one Room transaction. It validates a complete cloud snapshot at its pinned read time.
`ConflictPlan` compares the remote document versions to the local acknowledged bases and groups
related changes by stable cloud identity.

Transport mutation IDs do not make otherwise identical business values conflict. Attendance identity
remains `packageCloudId_epochDay`. Two devices recording the same visit count on the same class create
one resulting attendance, not the sum of two increments. Different counts or mark/remove disagreements
require a version choice. Two independently created renewals with different IDs also require review.
Different student IDs are not merged just because their names match.

Previously acknowledged cloud documents must still exist as live rows or tombstones. A missing cloud
head, reset cloud, malformed graph or unknown ownership is not permission to overwrite either side.
The normal safety checks remain in place; not every corrupted/manual cloud state is resolvable here.

## Durable review archive

Migration 9 to 10 adds `sync_conflict_reviews`; exported schemas 1 through 9 remain unchanged.
Each row stores the bound project/UID, a complete local snapshot, a complete cloud snapshot and the
chosen versions when applied. Local snapshots also retain the exact old frozen request if present.
Credentials and Firebase session tokens are not part of these models.

Review rows have three local statuses:

- `OPEN`: versions were captured, with no accepted decision yet
- `SUPERSEDED`: a new review replaced the old preview, whose contents remain available
- `APPLIED`: a decision was applied to Room and the durable outbox

`APPLIED` does not mean the server has accepted every queued upload. Existing upload acknowledgments
continue to track that separately. Reopening the database preserves both previews and pending work.
There is no automatic archive cleanup or user-facing archive/undo browser in this stage. Snapshots
are retained locally on the device that performed the review, not uploaded as shared account history.

## Applying a decision

The sync coordinator's cycle mutex serializes review operations with uploads and downloads. Before
applying, it reads the current server head online. Within one Room write transaction, the store:

1. checks the account, open review, unchanged cloud head and exact current local snapshot
2. builds the selected graph and validates dates, terms, references and attendance totals
3. checks upload-size limits for the resulting complete aggregate units
4. replaces business rows and preserves/reserves cloud-to-local ID mappings
5. acknowledges unchanged remote rows and queues only desired values that differ from the cloud
6. rebases those changes onto the actual remote document versions and account head
7. saves the new checkpoint and marks the review applied with its choices

A failure at any point rolls back business rows, outbox, frozen batch, checkpoint and review status.
A local edit or a new remote head invalidates the preview, loads fresh versions and clears selections.
It never applies an old selection to newly changed data without another explicit action.

If the remote head changes after the check, the subsequent upload still uses server `updateTime`
preconditions. Firestore rejects it, preserving the chosen local version and queue for another review.
Transient network failures during user confirmation leave the review open and require another attempt.
Normal local attendance actions remain available while sync is paused.

## Previously frozen uploads

A frozen user request is normally recovered before any pull. During explicit reconciliation, it can
be replaced only after its original immutable head differs from the captured cloud head. That server
version change fences the old request: it cannot later satisfy the original head precondition.

This proves that the old request cannot commit in the future; it does not guess whether it committed
in the past. The review uses the current cloud values and archives the exact old request either way.
If the original head is still current, the request must be recovered normally instead of discarded.
Newly rebased changes receive new mutation/batch IDs and retain the usual receipt recovery protocol.

## UI and lifecycle

`SyncConflictReviewDialog` lives in attendance `commonMain` so the product flow can be shared with a
future Android host. Desktop `SyncNotice` opens it from **Review changes**. The dialog scrolls, keeps
the apply action reachable, disables submission until all conflicts have a choice and blocks duplicate
submissions while applying. A second open request cannot replace a preview being selected. Finishing
an in-flight load after **Later** does not reopen the dismissed dialog.

The desktop session owns the coordinator and cancels work before closing Room. Local mode still
constructs no Firebase sync session. Idle polling, retry feedback and offline authentication behavior
are unchanged from the previous stage.

## Verification

- `commonTest/RoomConflictStoreTest`: version selection, independent changes, duplicate attendance,
  mark/remove, deletion/edit, concurrent renewals, dependent catalog deletion, tombstone ownership,
  stale previews, frozen-request fencing and oversized choices
- `commonTest/AttendanceConflictReviewTest`: paused-runner wake-up, refreshed previews, cancellation,
  temporary network failures, duplicate opens and dismissal during loading
- `desktopTest/SyncConflictReviewDialogUiTest`: explicit choices, per-student selection, stale-choice
  reset, disabled controls during apply, retry/postpone and no-overlap combine
- `desktopTest/AttendanceMigration9To10Test`: schema validation and preservation of existing state
- `desktopTest/RoomConflictStorePersistenceTest`: reopening and injected archive-write rollback
- `desktopTest/FirestoreConflictEmulatorTest`: actual device/cloud choices, old frozen requests and
  a real commit-precondition race after the resolution check

Emulator tests require `FIRESTORE_EMULATOR_HOST` pointing to loopback. They use demo-project owner
credentials and do not validate production Security Rules. No production Rules are deployed here.

## References

- [Android offline-first synchronization and conflict resolution](https://developer.android.com/topic/architecture/data-layer/offline-first)
- [Firestore write preconditions](https://firebase.google.com/docs/firestore/reference/rest/v1/Precondition)
- [Atomic Firestore commits](https://firebase.google.com/docs/firestore/reference/rest/v1/projects.databases.documents/commit)
