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
- Checkbox and people-count changes update a ViewModel-owned draft and its package progress. They do not write to Room or enqueue uploads.
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
- All devices sharing an account must upgrade before using this feature. The current protocol is 6 (including group deletion); older readers reject newer heads rather than silently losing class decisions. Previously frozen protocol 1–5 uploads retain their original body and receipt recovery.
- A date's pending class decisions and attendance changes are selected as one upload unit, with required new student/purchase parents. Unrelated queued dates can be sent in subsequent batches. Student deletion dependencies expand the unit when necessary.
- The existing limit of 400 business documents and 8 MiB per atomic upload remains. An oversized indivisible unit pauses with TOO_LARGE; the uploader never splits a decision from its attendance. Chunked class finalization is outside this MVP.
- Full snapshot import validates session identity, schedule reference, status and absence of attendance for cancelled sessions before replacing local data. Pull never creates echo uploads.
- A pending or remotely changed class decision links affected student versions into one conflict choice. Cancellation versus attendance is not resolved by blind last-write-wins. Both versions stay in the existing persisted conflict review until the teacher chooses.
- Existing account isolation, head preconditions, mutation IDs and durable outbox/receipt recovery remain in use.

## Deferred

- Reopen/edit a finalized or cancelled class.
- Reschedule a class, notify students, or automatically extend finite package validity after cancellation. Cancellation only prevents credit deduction.
- More than one attendance per student on the same local date, and changes to historical group membership.

## Student actions while tracking

- Add Student reuses registration, limited to groups linked to the selected schedule. A single eligible group is selected automatically.
- Purchase and first attendance dates are fixed to the open class date. Package selection, optional first attendance, Duo count and notes use the existing forms.
- Exhausted or expired latest packages offer Renew when there is no saved attendance for that class. A final saved visit remains a checkbox, even when it exhausts the package.
- Add Package opens the existing template form from registration or renewal and returns with the template selected. Cancelling it preserves the original form inputs.
- Student registration, renewal and template creation commit independently, immediately, through Room and its durable sync outbox. Discarding the attendance draft does not undo these actions; the forms disclose this.
- Returning to the roster incorporates the saved child action and preserves all other unsaved selections. Saving the class does not charge an already saved first visit twice.
- A child mutation revalidates the roster snapshot, class status and tracking window inside the same Room transaction. Stale or unavailable classes reject the mutation without partial writes. Close and reopen to reload; unrelated external changes are never silently merged into a dirty draft.
