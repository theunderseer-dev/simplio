## Implementation Status

### Implemented

#### Domain behavior

- custom student groups with normalized unique names;
- new weekly schedules or explicitly shared existing schedules;
- group-specific registration, renewal and attendance calendars;
- history shows one card per day, merges matching time intervals visually and loads five complete dates at a time;
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