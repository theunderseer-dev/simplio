# Schedule overlap validation

## Product rule

One account represents one teacher. Creating a distinct weekly schedule is rejected if any of its actual classes
intersects an existing schedule. Groups may refer to the same schedule: this remains one class with several groups.
The intervals are half-open: 09:00–10:00 and 10:00–11:00 are adjacent, not overlapping. Equal, partially overlapping,
and nested intervals collide. Inclusive schedule start/end dates must contain a real occurrence.

The group creation form reports the existing schedule name, disables Create while checking or on collision, and
rechecks whenever weekdays, times, dates or time zone change. The asynchronous result is associated with its draft;
a stale result cannot enable saving for a different draft. Checking runs outside the UI dispatcher and is cancellable.
RoomStudentGroupCreationRepository repeats the check inside the same transaction as schedule, group, links and outbox
writes. Rejected and concurrent saves cannot leave an orphan schedule, group link or queued mutation.

## Time zones and bounded work

Comparison reuses sessionsBetween and compares real Instant intervals. Different local dates and weekdays can collide
across time zones. Nonexistent wall times are skipped; repeated times use the same earlier-offset policy as attendance.
A class does not have to collide in the first week: seasonal clock changes can introduce a later overlap.

The common domain owns interval comparison and conflict results. A narrow expect/actual function supplies probe dates.
The desktop actual reads the installed IANA rules through java.time.ZoneRules. Between UTC-offset transitions both
weekly patterns repeat every seven days, so a short window at the start and around every transition is sufficient.
It does not generate all future classes or scan an arbitrary one-year/ten-year horizon.

After all fully defined zone transitions, annual transition rules and Gregorian weekdays repeat over a complete
400-year calendar cycle. The projection covers that cycle when future recurring rules exist; fixed-offset tails need
only a complete week. Finite schedule boundaries shorten the search. Before the first known transition, offsets are
fixed, so a representative initial week is sufficient for open historical ranges. This is exact for the installed
ZoneRules and the current weekly, same-local-day slot model; future legal time-zone changes require refreshed TZDB.

When another platform target is added, its actual must provide equivalent transition probes. Android can use the same
java.time facilities with the project's chosen API/desugaring configuration; an Apple target needs its own provider.

## Offline reconciliation and existing data

A device cannot know schedules created on another disconnected device. Such independently created series retain
separate IDs and both survive the existing sync review/merge. A time overlap is a business scheduling conflict, not
corrupt cloud data: imports must not discard valid groups or stop synchronization just because their times collide.

On every local-data invalidation, AttendanceViewModel reprojects overlaps from the locally stored catalog. The main
screen shows an amber notice naming each pair: both schedules are saved. It also detects pre-existing schedules and
historical overlaps introduced by retrospective registration extending a schedule's start date. The notice clears
when stored schedules cease to collide; it is derived data and needs no persisted conflict flag or new migration.

This change does not add schedule editing, cancellation or a resolution workflow for temporal overlaps. The notice
identifies the problem and preserves both records. Existing device/cloud review still handles version conflicts of
the same record. No Firestore protocol, schema version, production rules or data are changed.

## Validation

- compare interval boundaries, date periods, shared IDs, cross-zone weekdays and seasonal gaps/overlaps;
- compare transition probes with exhaustive occurrence projection over bounded calendars;
- verify transactional rollback and competing saves;
- verify creation feedback and recovery by changing times or choosing the shared schedule;
- reconcile independent offline groups with fake cloud and Firestore Emulator, preserving both on both devices;
- verify main-screen feedback and clearing it after a subsequent stored correction.

## References

- [ZoneRules: known transitions and future transition rules](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/time/zone/ZoneRules.html)
- [Android ZoneRules](https://developer.android.com/reference/java/time/zone/ZoneRules)

The product restrictions and reconciliation policy are project decisions. The APIs supply time-zone transitions;
they do not define this application's scheduling or conflict-resolution behavior.
