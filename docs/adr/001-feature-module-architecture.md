# ADR-001: Organize Simplio Around Feature Modules

## Status

Accepted

## Context

Simplio is intended to contain multiple independent personal automation
workflows over time.

The application starts with attendance management, but future capabilities
should be able to evolve independently without turning the desktop
application into a single tightly coupled module.

## Decision

Simplio will be organized primarily around feature modules.

Each substantial user-facing capability should own its feature-specific
behavior and implementation.

The desktop application acts as the application shell and is responsible for
platform integration, lifecycle, and composition of features.

Shared code should only be extracted when reuse between multiple features
actually exists.

## Consequences

### Positive

- Features can evolve and be tested independently.
- Business behavior remains separated from the desktop application shell.
- Module boundaries make dependencies explicit.
- Feature logic can potentially be reused by additional platforms.

### Trade-offs

- Multi-module Gradle configuration introduces some initial complexity.
- Dependency boundaries require deliberate maintenance.
- Cross-feature abstractions should not be introduced prematurely.