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
│   └── Desktop application shell
│
├── feature
│   └── attendance
│       └── Attendance-specific behavior and UI
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
- feature composition and navigation.
Feature-specific business rules should not live here.

### feature:attendance
Owns the attendance-management capability.
Its responsibilities include:
- student registration;
- package assignment;
- attendance recording;
- package usage tracking;
- attendance history;
- package renewal state;
- attendance-related presentation behavior.
  The feature should keep its business behavior independent from platform APIs
  and persistence technology where practical.

### shared
Contains code genuinely shared by multiple features.
Code should not be moved into shared in anticipation of possible future
reuse.

## Dependency Direction

Feature-specific business behavior should not depend directly on
desktop platform APIs or persistence implementations.

Platform and infrastructure modules may depend on feature-defined contracts,
but should not define feature business rules.

## Architecture Decisions

Significant architectural decisions are documented separately as
Architecture Decision Records:

- [ADR-001: Organize Simplio Around Feature Modules](adr/001-feature-module-architecture.md)