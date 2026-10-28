## Implementation Status

### Implemented

#### Domain behavior

- custom student groups with normalized unique names;
- custom package templates with unique names, optional validity in weeks and one/two-credit attendance;
- package purchases keep their terms independently of later catalog changes;
- new weekly schedules or explicitly shared existing schedules;
- group-specific registration, renewal and attendance calendars;
- history shows one card per day, merges matching time intervals visually and loads five complete dates at a time;
- track and finalize the latest started class per schedule, with a draft roster and Duo attendance counts;
- explicitly cancel a class without saved attendance; see [class attendance rules](class-attendance.md);
- offline group-name collisions are resolved by renaming the local group without merging students;

- student registration;
- assignment of an initial package;
- automatic first attendance during registration;
- attendance recording;
- prevention of duplicate attendance;
- package usage tracking;
- package completion detection;
- package expiration handling;
- package renewal;
- preservation of package purchase terms.

#### Persistence

- Room database schema;
- students, package types, student packages, and attendance persistence;
- Room repository implementations;
- atomic business operations through `TransactionRunner`;
- rollback behavior verified with integration tests;
- file-backed desktop database;
- persistence verified across database close and reopen.

#### Application initialization

- predefined 4-class and 5-class package types;
- idempotent default package seeding;
- macOS application database path;
- production Attendance dependency composition;
- database initialization on application startup;
- database lifecycle management on application shutdown.

### Not implemented yet

- roster/query API for displaying current students;
- presentation state;
- ViewModel;
- Compose Attendance UI;
- attendance history UI;
- renewal UI;
- scheduled reminders;
- manual Attendance navigation;
- copyable attendance summary.
## Renewal attendance boundary

A completed package may be renewed on its final attendance date as a purchase only. The first-attendance checkbox
stays visible, unchecked and disabled with an explanatory message. Its remembered user preference is restored when
a free date is selected; the expiration preview and Duo people selection follow the effective checkbox state.
A purchase without attendance has no activation or expiration date until its first valid later attendance.

For the current one-class-per-student-per-day model, attendance cannot be charged to two packages on the same date.
Renewal, ordinary marking and Room writes validate all locally stored packages inside the write transaction.
The calendar also excludes previous-package attendance dates after renewal. Existing historical data is retained;
there is no schema migration. Multi-device offline conflicts continue to use the existing synchronization workflow.

## Custom packages

Use **Add Package** after **Add Group** to define a reusable template. It contains a name, positive class capacity,
a nonnegative whole-unit price, optional validity in weeks and a one/two-credit attendance option. Dates and notes
are assigned when registering a student or renewing a purchase. Without expiration, only actual attendance consumes
credits; recurring schedule dates do not. See [custom package architecture](../architecture/custom-packages.md)
for migration, synchronization and compatibility details.

## Form input

All editable forms follow [Input validation](../architecture/input-validation.md), including numeric filtering,
name lengths, 24-hour schedule time, time-zone checks, paste rejection and sign-in input. Notes remain free text.

Group options beside Add Group provides deletion of empty groups and retirement of unshared schedules; see [group management](group-management.md).
