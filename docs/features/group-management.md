# Deleting student groups

## User flow

Group options (the overflow menu beside Add Group) opens Manage groups. The dialog lists all groups,
including empty ones, with student counts and their schedules. Occupied groups display a disabled delete action
and an explanation. An empty group's deletion requires confirmation; Cancel makes no changes.

Only groups without any students can be deleted, regardless of whether their packages are active or completed.
The condition is checked inside the same Room write transaction as deletion. A stale confirmation cannot bypass it.
Students, purchases and attendance are never removed by the group deletion operation.
Moving students to another group and reopening retired schedules remain separate future features.

## Schedule and history

Deleting a group removes its links and creates a durable sync tombstone. If another group still uses a schedule,
the schedule is unchanged. Otherwise it is archived at the deletion instant (UTC milliseconds).

An archived schedule keeps its identity, slots, time zone, historical class decisions and projections before that instant.
Classes that had already started remain in history, with their existing status. Classes starting after retirement
are no longer generated. Retirement does not mark past classes cancelled or deduct/refund package credits.
If independent offline deletions jointly remove the final groups, the schedule is retired at the reconciliation instant when the combined account graph loses its last group.
Retired schedules cannot be selected for a new group or block another schedule's interval.
A new group may reuse a deleted group's name but receives a new identity.

Default seeding respects group tombstones, including before the first cloud pull, so relaunch cannot recreate a deleted group.
Room schema 15 adds only nullable class_schedules.archivedAtEpochMillis. Historical schemas 1–14 are unchanged.

## Synchronization

Firestore protocol 6 adds schedule retirement semantics; schedule documents retain schemaVersion 2 with the optional
archivedAtEpochMillis field. New readers still accept protocol versions 1–5. Frozen older requests keep their body and receipt recovery.
All devices sharing an account must upgrade before using deletion; older clients reject the newer head.

Group tombstones, affected schedule retirement and preceding queued student deletions are expanded into a complete atomic
upload unit, together with required parents and related class decisions/attendance. Existing 400-document and 8 MiB bounds remain.
An oversized unit pauses instead of publishing an inconsistent graph. Snapshot import rejects live groups referencing an archived schedule.

Concurrent deletion versus registration becomes one persisted conflict review for the related group, schedule and student graph.
If the other device added a new student, the version that would remove that student is disabled and rejected by the conflict store too.
The teacher can keep the version preserving the student or postpone review. Deleting a group never implicitly authorizes deleting newly
added students or their packages. Explicit student deletions already made by the teacher retain the existing conflict behavior.
Legacy groups with an implicit default schedule are handled in both conflict directions without losing that schedule on restoration.
