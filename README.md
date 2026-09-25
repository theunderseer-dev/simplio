# Simplio

Simplio is a modular desktop application for automating recurring personal and operational workflows.

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
- Compose Multiplatform for Desktop
- Kotlin Coroutines & Flow
- Kotlin Test
- Gradle Kotlin DSL

### Planned as requirements emerge

- Room KMP / SQLite for local persistence
- macOS system notifications
- background execution and launch-at-login support
- structured feature navigation
- persistence and notification abstractions

Infrastructure is intentionally introduced only when required by executable behavior.

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

#