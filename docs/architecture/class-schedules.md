# Class schedules: foundation

## Scope

This change introduces schedule persistence and synchronization before adding the creation UI.
The existing attendance screen, date picker restrictions, date-based attendance records and history remain unchanged.
Custom schedules are not yet selectable in the product. Do not enable multiple sessions per day in the UI until
attendance and history identify sessions rather than only dates.

## Model

- A `ClassSchedule` is a weekly series in an explicit time zone, with optional inclusive start and end dates.
- A `WeeklyClassSlot` identifies a weekday and start/end wall times. Multiple slots per weekday are supported.
- `student_group_schedules` explicitly links groups to series. Several groups can share one series; equal names or
  times do not merge independent series. One student still belongs to one group.
- A `ClassSession` is calculated for a bounded date range. Its identity is the schedule ID, slot ID and local date.
  Group membership and display names are not part of that identity. Future occurrences are not stored indefinitely.
- Package purchases, activation, validity and actual attendance remain separate. A package's visit allowance does not
  determine a recurrence's end date; cancellations and absences can change the number of visits actually used.

Keep schedule and slot IDs stable when changing labels. Editing past schedules, exceptions, transfers, monthly rules,
notifications and deletion are outside this change. Supporting edits later requires preserving historical occurrences;
this foundation does not claim to supply that editing policy.

## Time zones

Store venue wall times and `TimeZone` IDs, resolve occurrences to `Instant`, and convert to the viewer's time zone
only for display. The device's current zone never determines series identity or its weekdays.
Nonexistent wall times during a daylight saving gap are skipped. For repeated times, the earlier offset is selected
consistently by kotlinx-datetime. Overnight slots are rejected in this first implementation.

## Migration and compatibility

Room migration 10 → 11 creates schedules, slots and group links. Every existing group is linked to the same legacy
Monday/Friday 09:00–10:15 `Asia/Bangkok` series. Student IDs, purchases, notes, attendance, checkpoints and frozen
requests are preserved. Schemas 1–10 are unchanged.

A legacy cloud group with no `scheduleIds` field implies that unchanged legacy series. This avoids manufacturing
pending writes for every existing account during migration. New databases and legacy cloud imports materialize the
same local template. An explicit schedule document with that ID overrides the template. Custom links require a live
schedule parent and are validated before replacing local rows.

Schedules use stable string IDs both locally and remotely; they do not participate in integer ID allocation.
They are stored under the existing user's `classSchedules` collection. Group links are synchronized in the group
record, while all slots are synchronized atomically in one schedule document. Local writes and outbox changes share
one Room transaction. Catalog uploads send schedule parents before groups.

New batches use sync protocol 2. Earlier clients accept only protocol 1 and will stop synchronization rather than
silently discard new entity types; update all devices before adopting the new version. A frozen protocol-1 request
retains its original body and receipt format so an update does not break recovery after a lost response.

Conflict review treats a schedule and its slots as one unit. Concurrent versions are retained in the existing review
archive and resolved with the existing device/cloud choice. Group-parent dependencies are validated on merge.

## Next focused PRs

1. Add Group and its form: name uniqueness, weekly schedule selection/creation and save error handling.
   Connect registration, marking and history to the selected schedule and session identity as part of enabling this UI,
   before allowing independent sessions on the same date. Keep a common series as one class when several groups attend.
   Preserve the current main screen and defaults; defer onboarding. Check duplicate names in the form and transaction,
   and define the rename conflict for concurrent offline group creation.
2. Add schedule editing and exceptions with an explicit policy for historical classes.
3. Add custom packages and notifications as separate features.

## References

- [Android architecture recommendations](https://developer.android.com/topic/architecture/recommendations)
- [Offline-first data layer](https://developer.android.com/topic/architecture/data-layer/offline-first)
- [Recurring events and instances](https://developers.google.com/workspace/calendar/api/guides/recurringevents)

The domain choices above are project decisions. These sources inform separation of concerns and occurrence identity;
they do not prescribe this application's Room schema or sync protocol.
