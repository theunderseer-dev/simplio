# Simplio repository instructions

These instructions apply to Codex and other agents working in the Simplio repository. Simplio uses Kotlin Multiplatform, Compose Multiplatform for macOS Desktop, Room and SQLite, Koin, and Firebase REST. Check the current branch and any instructions that apply to the directories you are changing. Use Android Developers guidance for principles that carry over to desktop development. Check the repository and the documentation for the versions in use when choosing desktop APIs or commands.

## Development workflow

1. Check the branch and working tree. Read `docs/development-workflow.md`, `docs/architecture.md`, and the documentation for the area you are changing. Find the normal entry point, its callers, and the nearest tests. Preserve the user's edits and staging state.
2. Define the expected behavior, edge cases, and verification steps. Reproduce bugs when possible. For new or changed behavior, follow red, minimal green, then refactor. Confirm that the regression test fails for the expected reason before the fix. Preserve what existing tests verify.
3. Make the smallest complete change. When changing a contract, update its callers, implementations, dependency injection, and tests in the same logical change. Update the relevant documentation when behavior changes. If multiple agents are already working in parallel, give them separate worktrees or non-overlapping areas.
4. Review the final diff and run the applicable checks below. If asked for the next step, complete the agreed step. Use lowercase Conventional Commit messages, for example `fix(attendance): preserve attendance history`. For manual ZIP delivery, include complete changed files under `changes/` with repository-relative paths, plus `PR_TITLE.txt` and an English `PR_DESCRIPTION.md` with Summary, Changes, Testing, and Verification sections. Follow explicit user direction for pushes, merges, and production deployments.

## Architecture and code quality

- Respect module ownership. `desktopApp` owns startup, dependency injection wiring, the window, database paths, and native adapters. `feature:attendance` owns the attendance domain, use cases, persistence, and UI. Use `shared` for code with actual reuse across feature or module boundaries. Keep the domain independent of Compose, DAOs, HTTP, and macOS APIs.
- Maintain unidirectional data flow. Screens render state and send user actions. A ViewModel typically owns screen state and exposes it as read-only. Temporary state for an individual element can stay in the UI. Keep attendance and pass rules in the domain and use cases. Room is the source of persisted attendance data. Access it through existing repository contracts in the UI.
- Follow `.editorconfig`, ktlint, Kotlin conventions, and the surrounding code. Use clear domain names, the narrowest appropriate visibility, and explicit types for significant states and errors. Validate nullability and invalid inputs at boundaries. Avoid `!!` on external input, silent fallbacks, duplicate business rules, unnecessary suppressions, and unrelated formatting. Introduce abstractions for a concrete need, such as existing reuse or a platform boundary. When adding a dependency, verify source-set support, licensing, and compatibility with the Gradle version catalog.
- Scope coroutines and observers to their owner, such as a ViewModel, account, or window. Release resources and stop work from the previous session on sign-out or account switch. Keep blocking I/O off the UI thread. Preserve `CancellationException` when handling failures with `catch` or `runCatching` around suspend calls. Use specific error types. Use Compose effect APIs with keys that match the intended lifecycle. Prevent recomposition from repeating writes.
- Reuse Simplio's themed components. For interactive elements, check keyboard operation, focus, accessible names, and state semantics. Convey status with text, an icon, or an accessible description alongside color. For user-facing changes, check applicable loading, empty, and error states and both themes.

See [Android Developers on architecture](https://developer.android.com/topic/architecture/recommendations), [coroutines](https://developer.android.com/kotlin/coroutines/coroutines-best-practices), [Compose effects](https://developer.android.com/develop/ui/compose/side-effects), [Compose semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics), and [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html).

### KMP modularity and code reuse

Simplio currently builds for the JVM. `:desktopApp` and `:feature:attendance` declare `jvm("desktop")` with `desktopMain` and `desktopTest`. `:shared` declares `jvm()` with `jvmMain` and `jvmTest`. The macOS app runs on the JVM and accesses native APIs through JNI. Verify targets and source sets against the Gradle files on the current branch. Add an Android or iOS target when platform support is part of the task.

Before extracting code, find the existing implementation and its owner. Check whether the callers share the same contract, business meaning, and reasons to change. Keep each shared rule in one place and update affected callers to use it. Similar syntax alone is insufficient reason to couple independent rules. Choose the owning module by responsibility, then choose the source set by its target consumers. `:shared` is a specific Simplio module. `commonMain` is a source set available to every configured target in its KMP module.

| Reuse scope | Where the code belongs |
| --- | --- |
| Multiple screens or targets of one feature | Keep the code in the feature module. Keep pass validity and attendance rules in the attendance module's `commonMain`. Place platform implementations in the appropriate source set or app adapter. |
| Multiple features or modules across platforms | Use an existing shared module with the right responsibility, such as `:shared` for a reusable component. Use `commonMain` when the contract and its dependencies are available to every target in that module. If native UI consumers need access to business logic without Compose, keep that dependency boundary explicit. |
| Related targets in one KMP module | Use the most specific shared source set that covers all consumers and supports the required APIs. For example, use `iosMain` across iOS device and simulator targets once configured. Start with Kotlin's default hierarchy. Check whether it covers the required combination before adding manual `dependsOn` edges. |
| Multiple modules on one platform | Reuse code through an existing module on that platform with the right responsibility. Extract a platform-specific module when needed, such as an Android library used by several Android features or a local Swift package for shared native iOS code. For callers in one module, a package within its source set is usually enough. |
| Platform operation or lifecycle | Keep OS calls, permissions, windowing, file paths, Keychain, and JNI in platform implementations. Keep entry points and dependency injection wiring in app modules. Common code can depend on a narrow contract, such as `FirebaseSessionStore` in `:shared`'s `commonMain`, implemented by `MacFirebaseSessionStore` in `:desktopApp`'s `desktopMain`. |

- Let app modules assemble features and implementations. Keep feature and shared modules independent of app modules. Avoid dependency cycles and direct access to another feature's internals. When behaviors share data, use the existing data owner and a narrow contract. Use `implementation` for internal dependencies and `api` when public contracts expose types consumers need. Split API and implementation into separate modules only when an explicit boundary is needed.
- Keep platform types and dependencies in source sets whose targets all support them. Prefer a Kotlin interface and existing dependency injection for platform services that have replaceable implementations. Use `expect`/`actual` for a small API with known platform implementations when it simplifies the contract. Verify implementations on each affected target.
- Reuse screen state, actions, and Compose components where their contracts agree. Keep navigation, gestures, keyboard behavior, focus, and permissions in the relevant platform layer where they differ. When moving code, check resources, dependencies, callers, dependency injection, and existing behavior. Keep shared tests in `commonTest` and adapter and system-integration tests in source sets that run on the relevant targets. Run the matching build and test tasks for every affected configured target.

See [JetBrains on KMP project structure](https://kotlinlang.org/docs/multiplatform/multiplatform-project-recommended-structure.html), [source set hierarchy](https://kotlinlang.org/docs/multiplatform/multiplatform-hierarchy.html), [platform implementations and dependency injection](https://kotlinlang.org/docs/multiplatform/multiplatform-expect-actual.html), [platform differences in Compose](https://kotlinlang.org/docs/multiplatform/compose-platform-specifics.html), [Android Developers on modularization](https://developer.android.com/topic/modularization), [modularization patterns](https://developer.android.com/topic/modularization/patterns), [Apple on local Swift packages](https://developer.apple.com/documentation/xcode/organizing-your-code-with-local-packages), and [Gradle on API and implementation dependencies](https://docs.gradle.org/current/userguide/java_library_plugin.html#sec:java_library_separation).

## Data, synchronization, and scaling

- Use the existing use cases, tracked repositories, and `RoomSyncJournal` for application writes. Validate current state, write related local entities, and update the durable outbox in one Room transaction. Run network calls outside that transaction. Preserve data integrity and return a clear result when state is stale, a conflict occurs, or an operation fails.
- When changing sync, check retries, account changes, races, and connection loss after sending a request. A timeout leaves the outcome unknown. Preserve stable IDs, head and version preconditions, immutable in-flight batches, receipts, and tombstones. Acknowledgments for older requests must preserve newer local edits. Validate the complete snapshot before applying a pull and avoid uploading the same changes back. Use existing conflict resolution and keep related writes atomic.
- For Room schema changes, add a migration, export the new schema, and test upgrade with existing data. Preserve historical schema exports, IDs, relationships, and the terms of purchased passes. Verify relevant upgrade paths from supported versions and reopen the database after migration. Avoid destructive migrations of user data. For sync protocol changes, verify existing payload compatibility, behavior across client versions, and any upgrade requirements. Check protocol and schema versions against the current branch.
- Bound reads and batch sizes as collections grow. Use existing pagination. For new hot-path queries, check the selection, indexes, and number of SQL or Firestore operations. Use cursors for new paginated Firestore queries. Measure meaningful changes with representative synthetic data. Preserve atomicity when handling batch limits. Follow the existing retry policy and distinguish transient failures, conflicts, cancellation, and permission denials.
- When working with attendance and notification dates, use the schedule time zone and the existing domain model. Distinguish purchase date from first visit. Where relevant, test expiry boundaries, repeated cancellations, and calendar transitions. See `docs/features/class-attendance.md` and `docs/features/cancelled-class-validity.md` for business rules. See `docs/architecture/sync-outbox.md` and the accompanying `firestore-upload.md`, `firestore-pull.md`, and `sync-conflicts.md` for sync behavior.

See [Android Developers on Room migrations](https://developer.android.com/training/data-storage/room/migrating-db-versions), [Firebase on atomic writes](https://firebase.google.com/docs/firestore/manage-data/transactions), and [Firestore performance guidance](https://firebase.google.com/docs/firestore/best-practices).

## Security and privacy

- Isolate workspace data by account locally and in Firestore. Enforce access through `firestore.rules` independently of client UI filtering. Preserve Simplio's matching UID and verified email requirements for existing private collections. Grant only the access each new read or write requires. For authentication or rules changes, test the account owner, a different UID, an unverified email, and an unauthenticated request. For existing receipts, verify that reads are allowed for the authorized user and updates and deletions are denied.
- Validate external inputs and sync data at their trust boundaries. Use DAOs or bound SQL parameters. Preserve HTTPS and the system's TLS verification. Keep service account credentials out of the client, repository, and build artifacts. Store only the data required by Remember me in Keychain. Keep passwords, tokens, real email addresses, and student data out of logs, fixtures, and reports. Use de-identified data, a separate test account, or a local emulator. Change production data or configuration and publish rules only with explicit user direction.
- Include enough context to diagnose failures without exposing sensitive data. Surface authorization, Keychain, and sync failures accurately. When changing authentication or account isolation, verify resource cleanup after sign-out and account isolation at the next sign-in.

See [Firebase Security Rules conditions](https://firebase.google.com/docs/firestore/security/rules-conditions), [testing rules in the emulator](https://firebase.google.com/docs/firestore/security/test-rules-emulator), and [Apple Keychain Services](https://developer.apple.com/documentation/security/keychain-services).

## Tests and acceptance checks

| Area | What to verify |
| --- | --- |
| Domain and ViewModel | Cover business rules, boundary values, and failures in nearby `commonTest` tests. Use controllable clocks, fakes, and test dispatchers where needed. Assert observable results and state transitions. |
| Room and sync | Exercise real queries and migrations against a test SQLite or Room database. Check rollback when a related step fails. For sync changes, cover compatibility, retry, and conflict behavior for the affected scenario. |
| Compose | Select nodes by semantics and screen scope. When a dialog overlays a list, scope selection to the dialog because the same label can appear in both. Assert actions and their outcomes. Use the test API's synchronization tools for asynchronous UI. |
| Firestore Rules | Test against the emulator with user permission contexts. Check allowed and denied operations on affected paths. |
| macOS, Keychain, and JNI | Build the package on a Mac with `./gradlew :desktopApp:createDistributable`. For affected scenarios, verify the library inside the `.app`, launch the built bundle, and exercise the relevant user actions. |

See [Android Developers on the Compose Test API](https://developer.android.com/develop/ui/compose/testing/apis) and [JetBrains on Compose native distributions](https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html).

For code changes, run the targeted Gradle tasks first. Before preparing a PR, check the commands in `.github/workflows/ci.yml`. Run Simplio's baseline checks from the repository root.

```bash
./gradlew :feature:attendance:desktopTest
./gradlew :shared:jvmTest
./gradlew :desktopApp:desktopTest
./gradlew check ktlintCheck detekt :feature:attendance:koverVerify
```

For Firestore Rules changes, run the dedicated tests.

```bash
npm --prefix tools/firestore-rules ci
npm --prefix tools/firestore-rules test
```

Point `FIRESTORE_EMULATOR_HOST` at a running local emulator for Gradle tests that require it. For documentation-only changes, verify the content and links and run `git diff --check`. Report the checks that passed and explain any skips. If the environment cannot build the project or verify native behavior, run the available checks and name the remaining command or scenario. Verify packaged macOS launch separately from JVM checks.

## Code Review Rules

1. Report defects introduced by the change. Point to the file and the scenario that triggers the problem. Prioritize data loss, cross-account access, duplicate deductions, migration incompatibility, and regressions in the normal workflow.
2. Check ownership of shared logic, dependency direction, source-set compatibility, error handling, and resource lifecycles. Review the effect on queries, UI latency, and batch sizes in proportion to the change.
3. Verify that tests exercise normal entry points and detect the regression they cover. For unconfirmed risks, explain how to reproduce them. Suggest changes that address a specific observable problem.
