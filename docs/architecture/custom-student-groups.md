# Custom student groups

## User flow

`Add Group` follows `Add Student` as a secondary action and wraps onto another line on small windows.
The main screen and existing Group A/B defaults remain. Empty groups remain hidden in the attendance list;
creation shows a confirmation and makes the new group available in registration.

A teacher enters a unique group name and either shares an existing schedule or creates a weekly series:
weekdays, start/end times, venue time zone, inclusive start date and optional inclusive end date.
An existing series means the same class for its groups. Matching times on separately created series do not merge them.
Registration displays group choices as wrapping chips rather than a fixed-width segmented row.

This first creation UI allows one series per group and at most one occurrence per weekday in that series.
Students still belong to one group and can have at most one attendance per package/date. The schedule foundation
can represent more slots, but this UI does not enable multiple same-day classes for one student.

The selected group's dates apply to registration, renewal and marking. Registration and renewal allow any past
occurrence of the weekly pattern, even before the series was created; there is no rolling-month limit. The venue's
current day is allowed, future days and days after an explicit series end are unavailable. Saving an earlier purchase
extends the matching series start to that date in the same Room transaction as the package and outbox writes. Shared
groups retain the same series identity. This also repairs eligibility for groups already created with today's start.
Purchase-date restrictions stay the same when the first-attendance checkbox is off, and renewal retains its previous
package boundary. Marking still respects the persisted series and package purchase/activation boundaries. Attendance
is day based: a class can be marked on its scheduled date before its start time. Changing this to an exact start-time
restriction would be a separate product decision.

## Names and transactions

`normalizedGroupName` trims and collapses whitespace and uses invariant lowercase. Names must contain 1–80 characters.
The form disables Create and shows an inline duplicate-name message. The creation transaction repeats validation,
checks the selected schedule, inserts the group/link/new series and journals cloud changes together.
`student_groups.nameKey` has a unique index as a final storage guard.

Migration 11 → 12 preserves existing names, including any pre-feature duplicates. Old duplicates get distinct internal
compatibility keys; they are not silently renamed. New creation checks actual display names as well, so it cannot add
another normalized duplicate. The key is derived locally and is not a second remote source of truth.

Saving state prevents duplicate submissions and dismissal during save. A failure stays in the group dialog and allows
retry without replacing the attendance screen. Room invalidations include schedules and links, so remote changes
refresh group choices and date eligibility.

## Offline duplicate names

Independent offline creations have different stable cloud IDs. Synchronization must not merge their students just
because names match. Conflict planning identifies a pending group whose normalized name was used by another live
cloud group and requires a new name for the local group. The existing cloud group is preserved.

The review UI validates the new name, shows both sets of details and cannot apply until the name is available.
The merge rechecks uniqueness against the merged graph. It preserves each group's ID, schedule and student references.
Both versions and the chosen rename are retained in the existing local conflict archive. A stale review still requires
refreshing before applying. Other concurrent edits use the existing device/cloud choices.

## Attendance identity and history

New attendance for a custom series stores the calculated session ID. Import checks that the ID is an occurrence of
one of the owning group's schedules on that date. Legacy attendance remains readable without a session ID; it is
projected from the unchanged legacy schedule or retained as a historical date outside a series.

History preserves custom class identities by series/slot/date and includes empty scheduled classes since the first
recorded attendance. The screen shows one card per date with the existing date/day title and a daily visit total.
Inside the card, time blocks sort by actual start instant, latest first. Separate series with identical start AND end
instants share one visible block; series IDs, group links and attendance references remain independent in storage.
The label contains only venue time and time zone; group names remain under students. Visits across packages sum for
one student's row within a block. An empty coinciding series does not add an empty-class message to a populated block.
Unknown legacy times stay in a separate block and are not guessed from another series.

Pagination returns five complete dates, including every class on the fifth date. The next cursor is the exclusive
oldest loaded date, without a session cursor. A large number of same-date classes therefore cannot split a day's card
between pages. Pages may contain more than five underlying session entries; this does not mutate remote data.

Schedule lookups jump to the previous occurrence or recorded date rather than scanning every day of an inactive period.
A clock injected from the desktop composition root updates venue dates every 30 seconds; a venue's midnight does not
have to coincide with the device's midnight. Tests can inject a deterministic flow of instants.

## Protocol compatibility

New requests use sync protocol 3. Versions 1 and 2 remain readable, and already-frozen requests keep their original
body and receipt format. Older clients stop on an unfamiliar head version. Update devices together before relying on
custom date rules. Schedule/group documents retain the existing version-2 field representation; attendance with a
session reference also uses schema version 2. Protocol version identifies client behavior, not only document shape.

No production Firestore data or rules are changed by the test run. Emulator coverage does not substitute for Security
Rules testing. All data stays under the current user's existing document hierarchy.

## Deferred

Onboarding, group/schedule editing, group deletion, group transfers, several classes per student per day, recurrence
exceptions, monthly rules, custom package plans and notifications are separate features. In particular, schedule editing
must preserve recorded session identity; changing a series in place is not an implemented historical-edit policy.
