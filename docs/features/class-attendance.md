# Class attendance and cancellation

## Tracking window

- Track attendance belongs to the latest started occurrence of each schedule. It expires exactly when the next occurrence of that schedule starts, in the schedule time zone.
- Shared groups referring to one schedule share one session and one roster. Independent schedules retain their identity even when the history groups equal time intervals visually.
- Future same-day sessions are excluded from history when a current instant is supplied.
- A saved or cancelled session has no Track/Edit action in this MVP. Older unrecorded sessions remain unrecorded; time passing does not imply cancellation or zero attendance.
- History starts from the earliest recorded attendance, package purchase or explicit class decision. A student with an unactivated package can therefore be tracked from class history.

## Dialog and persistence

- The roster contains students from all groups assigned to the selected schedule. Existing attendance selects its actual purchased package; otherwise the latest purchase on or before the session date is considered.
- Existing visits stay checked even when they complete or outlive a package. Existing visits can be unchecked before the class is finalized.
- New attendance requires a package valid for the session date, sufficient remaining credits and no other attendance for that student on the same date. The existing one-attendance-per-student-per-date restriction remains.
- Duo uses 1 person / 2 people. The choice cannot exceed the remaining credits, including credits already recorded for this session.
- Checkbox and people-count changes update a dialog-local draft and its package progress. They do not write to Room or enqueue uploads.
- Save validates the immutable roster/package snapshot again under the Room write transaction. A changed roster, attendance or package rejects the entire save and preserves the dialog draft.
- The tracking window and explicit status are also rechecked at Save. No partial attendance or outbox changes are committed on failure.
- Save commits attendance changes, derived package activation, an explicit HELD decision and outbox changes in the same local transaction.
- Closing a modified draft requires confirmation. While saving, closing and double submission are disabled. A successful local save closes the dialog; a later network failure does not undo it.

## Class states

| Stored decision | Attendance | History |
| --- | --- | --- |
| No row | None | Attendance not recorded |
| No row | Present | Existing individually recorded attendance, still eligible for finalization within its window |
| HELD | None | No attendance |
| HELD | Present | Student attendance list |
| CANCELLED | None | Class cancelled |

Cancel class is available only when no saved attendance exists and the draft selects nobody. Confirmation saves CANCELLED without changing any package. Unchecking previously saved visits does not enable cancellation in the same draft.

Cancelled sessions reject ordinary student checks and registration/renewal that includes first attendance. Buying a package without attendance remains allowed. Existing individual attendance correction remains available for held sessions; class-level editing, reopening and rescheduling are deferred.

## Cloud synchronization

- Room schema 14 adds class_session_states; migration 13→14 preserves existing tables and queued/frozen work. Historical schema JSON files are unchanged.
- Firestore protocol 5 adds users/{uid}/classSessions/{sessionId}, with scheduleId, dateEpochDay and status, plus the existing schema/mutation metadata. Session IDs are stable across devices.
- All devices sharing an account must upgrade before using this feature. Older readers reject protocol 5 rather than silently losing class decisions. Previously frozen protocol 1–4 uploads retain their original body and receipt recovery.
- A date's pending class decisions and attendance changes are selected as one upload unit, with required new student/purchase parents. Unrelated queued dates can be sent in subsequent batches. Student deletion dependencies expand the unit when necessary.
- The existing limit of 400 business documents and 8 MiB per atomic upload remains. An oversized indivisible unit pauses with TOO_LARGE; the uploader never splits a decision from its attendance. Chunked class finalization is outside this MVP.
- Full snapshot import validates session identity, schedule reference, status and absence of attendance for cancelled sessions before replacing local data. Pull never creates echo uploads.
- A pending or remotely changed class decision links affected student versions into one conflict choice. Cancellation versus attendance is not resolved by blind last-write-wins. Both versions stay in the existing persisted conflict review until the teacher chooses.
- Existing account isolation, head preconditions, mutation IDs and durable outbox/receipt recovery remain in use.

## Deferred

- Add Student, Renew Package and Add Package inside the class dialog; use the main screen for these actions in this PR.
- Reopen/edit a finalized or cancelled class.
- Reschedule a class, notify students, or automatically extend finite package validity after cancellation. Cancellation only prevents credit deduction.
- More than one attendance per student on the same local date, and changes to historical group membership.
