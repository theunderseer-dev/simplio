# Architecture

## Overview

Simplio is a modular Kotlin Multiplatform desktop application built around
independent automation features.

The initial target is macOS Desktop using Compose Multiplatform.

The architecture is intentionally developed incrementally from behavioral
requirements rather than fully designed upfront.

## Current Modules

```text
Simplio
│
├── desktopApp
│   └── Desktop application shell and feature composition
│
├── feature
│   └── attendance
│       └── Attendance behavior, persistence, and presentation
│
└── shared
    └── Cross-feature code when genuine reuse emerges
```

### desktopApp

Responsible for desktop-specific application concerns:

- application entry point;
- application lifecycle;
- window management;
- menu bar integration;
- macOS-specific integrations;
- feature composition and navigation;
- application-specific database location;
- initialization and lifecycle of feature dependencies.

Feature-specific business rules should not live here.

For Attendance, `desktopApp` currently owns the application composition root,
provides the macOS database path, initializes default package types, and closes
the feature database when the application exits.

### feature:attendance

Owns the attendance-management capability.

Its responsibilities include:

- student registration;
- package assignment;
- attendance recording;
- package usage tracking;
- attendance history;
- package renewal state;
- persistence of attendance data;
- attendance-related presentation behavior.

The feature keeps its business behavior independent from desktop platform APIs
where practical.

The feature uses Kotlin Multiplatform source sets to separate reusable behavior
from desktop-specific infrastructure:

```text
commonMain
├── domain models and rules
├── repository contracts
├── use cases
├── Room schema and DAOs
├── Room repository implementations
├── transaction handling
└── default package seeding

desktopMain
└── file-backed AttendanceDatabase creation
```

`commonTest` contains platform-independent behavior and persistence integration
tests.

`desktopTest` contains tests that require desktop/JVM infrastructure, such as
opening and reopening a file-backed database.

### shared

Contains code genuinely shared by multiple features.

Code should not be moved into `shared` in anticipation of possible future reuse.

## Persistence and Composition

Attendance persistence uses Room with SQLite.

The Attendance feature defines how its database is structured and how an
Attendance database is opened from a supplied file path.

The desktop application decides where the database is stored.

On macOS, Simplio stores its database under:

```text
~/Library/Application Support/Simplio/simplio.db
```

The current dependency and composition flow is:

```text
desktopApp
    ↓
AttendanceComponent
    ↓
use cases / repositories / transaction runner
    ↓
AttendanceDatabase
```

The presentation layer should depend on feature-facing APIs rather than
accessing DAOs or the database directly.

## Dependency Direction

The current module dependency direction is:

```text
desktopApp
    ↓
feature:attendance
    ↓
shared
```

Feature business behavior must not depend on `desktopApp`.

The desktop application may depend on feature implementation details required
for application composition, but business rules remain owned by the feature.

Platform-specific concerns such as the macOS application data directory belong
to `desktopApp`.

Feature-specific infrastructure that does not depend on the application shell
belongs to the feature module.

Code should only move into `shared` when genuine reuse across features appears.

## Architecture Decisions

Significant architectural decisions are documented separately as Architecture
Decision Records:

- [ADR-001: Organize Simplio Around Feature Modules](adr/001-feature-module-architecture.md)
