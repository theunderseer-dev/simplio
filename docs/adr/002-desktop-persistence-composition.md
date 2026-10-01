# ADR-002: Compose Desktop Persistence at the Application Boundary

## Status

Accepted

## Context

Simplio currently targets macOS Desktop and uses a modular Kotlin Multiplatform
architecture.

The Attendance feature owns its business behavior and persistence model, while
`desktopApp` acts as the application shell.

Attendance persistence requires two distinct responsibilities:

1. defining and opening the Attendance database;
2. deciding where the application stores that database on the current platform.

Keeping both responsibilities inside the Attendance feature would make the
feature aware of application-specific and macOS-specific filesystem concerns.

Keeping all persistence setup inside `desktopApp` would instead move
Attendance-specific infrastructure out of the feature that owns it.

A composition boundary is therefore needed between the application shell and
the Attendance feature.

## Decision

Persistence responsibilities are split between `feature:attendance` and
`desktopApp`.

### feature:attendance

The Attendance feature owns:

- the Room database schema;
- DAOs and entities;
- Room repository implementations;
- transaction handling;
- default package seeding;
- creation of a file-backed `AttendanceDatabase` from a supplied file path.

Platform-independent Attendance persistence code lives in `commonMain`.

Desktop-specific database creation lives in `desktopMain`.

The feature does not decide the application-specific location of its database.

### desktopApp

The desktop application owns:

- the macOS-specific database location;
- creation of the Attendance composition root;
- wiring repositories, transactions, use cases, and initialization;
- initialization of default package types at application startup;
- database lifecycle management at application shutdown.

The current macOS database location is:

```text
~/Library/Application Support/Simplio/simplio.db
```

The composition flow is:

```text
desktopApp
    ↓
AttendanceComponent
    ↓
use cases / repositories / transaction runner
    ↓
AttendanceDatabase
```

`AttendanceComponent` is currently a manual composition root.

The presentation layer should depend on feature-facing APIs rather than
accessing `AttendanceDatabase` or DAOs directly.

## Rationale

This split keeps application-specific filesystem policy outside the Attendance
feature while allowing Attendance-specific persistence implementation to remain
owned by the feature.

It also preserves the dependency direction:

```text
desktopApp
    ↓
feature:attendance
```

The feature does not depend on the application shell.

The approach supports incremental migration to a dependency-injection framework
later without requiring changes to the feature's business rules.

## Consequences

### Positive

- Attendance persistence remains owned by the Attendance feature.
- macOS-specific filesystem concerns remain in `desktopApp`.
- Feature business behavior does not depend on the desktop application shell.
- The database location can change without changing Attendance domain behavior.
- File-backed persistence can be tested independently of the application path.
- The current manual composition root provides an explicit dependency graph.
- A future DI container can replace manual wiring without changing the intended
  dependency direction.

### Trade-offs

- `desktopApp` currently knows concrete Attendance infrastructure classes in
  order to compose the feature.
- The manual composition root contains explicit construction code.
- Desktop-specific source sets are required even though macOS is currently the
  only supported platform.
- Database lifecycle must be managed explicitly until a DI container or another
  lifecycle owner assumes that responsibility.

## Alternatives Considered

### Store the database path inside `feature:attendance`

Rejected because the Attendance feature would become aware of Simplio-specific
and macOS-specific application storage policy.

### Build the entire Attendance persistence stack inside `desktopApp`

Rejected because Attendance-specific repositories, transactions, and database
implementation belong to the feature that owns the capability.

### Introduce a dependency-injection framework immediately

Deferred.

The current dependency graph is small enough to keep explicit. A DI framework
can be introduced when composition complexity justifies it, without changing
the architectural boundary established by this decision.
