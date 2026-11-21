# Simplio

Simplio is a modular application for automating recurring personal and operational workflows on macOS and iOS.

The goal is to reduce repetitive administrative work by turning recurring tasks into small, focused workflows with reminders, structured input, local persistence, and automation.

The project is being built incrementally, starting with a real-world class attendance workflow.

## Current Feature

### Attendance

The first Simplio module automates group-class attendance management.

It is designed to:

- remind the user to record attendance after scheduled classes;
- display students with their current package status;
- register new students and assign predefined packages;
- automatically record the first attendance when a package is created;
- track attendance history and package usage;
- identify packages that require renewal;
- persist state between application sessions;
- generate a summary that can be copied and shared.

The feature is developed from behavioral requirements before implementation details are introduced.

## Tech Stack

### Current

- Kotlin
- Kotlin Multiplatform
- Compose Multiplatform for Desktop and iOS
- Kotlin Coroutines & Flow
- Room KMP and SQLite
- Firebase Authentication and Firestore REST
- macOS system notifications
- Kotlin Test
- Gradle Kotlin DSL

### Planned as requirements emerge

- background execution and launch-at-login support
- structured feature navigation
- persistence and notification abstractions

Infrastructure is intentionally introduced only when required by executable behavior.

## Run on iOS

Open `iosApp/iosApp.xcodeproj` in Xcode with the shared `Simplio` scheme. See the [iOS setup and architecture](docs/architecture/ios-attendance.md) for signing, Firebase configuration, and local verification.

## Development Approach

Simplio follows a behavior-first development process (BDD) that emphasizes the importance of understanding and defining the desired behavior of the application before writing code:

```text
Requirement
    ↓
BDD scenario
    ↓
Executable test
    ↓
Minimal implementation
    ↓
Refactoring
```

Implementation follows a TDD cycle: RED → GREEN → Refactor.
Completed, verified milestones are committed incrementally.

See [Development workflow](docs/development-workflow.md) for TDD, verification, and commit guidelines.

#
