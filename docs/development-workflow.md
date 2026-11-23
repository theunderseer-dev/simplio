# Development workflow

Build features through small, complete milestones. Commit each verified milestone rather than accumulating every change until the entire feature is finished.

## Branch naming

Name each new working branch `<type>/<short-description>`. Choose the type by the branch's primary purpose and describe the change in a few lowercase words separated by hyphens. Use a single slash and make the change type the only prefix, including for branches created by automation.

| Type | Primary purpose | Example |
| --- | --- | --- |
| `feat` | New capability | `feat/manage-packages` |
| `fix` | Bug fix | `fix/keychain-startup-prompt` |
| `refactor` | Code restructuring that preserves behavior | `refactor/koin-di` |
| `docs` | Documentation | `docs/branch-naming` |
| `test` | Test coverage | `test/attendance-room-integration` |
| `chore` | Project maintenance | `chore/code-quality` |

Use more specific types when they fit, such as `build`, `ci`, `perf`, or `style`. Before committing, confirm the current branch matches the task with `git branch --show-current`. When pushing, publish it under the same name and use that branch as the PR head. Choose each commit's type for the change in that commit.

The short, descriptive topic name follows AOSP guidance. Simplio uses its existing type prefixes to make the purpose visible in the branch name. [AOSP Repo command reference](https://source.android.com/docs/setup/reference/repo#start)

## TDD cycle

Follow BDD → TDD for every feature and bug fix.

1. **BDD.** Define the expected behavior, relevant edge cases, verification steps, and intended commit boundary for the next milestone.
2. **RED.** Start with an executable test for the scenario before changing production behavior. For a fix, use a regression test that reproduces the bug. Add minimal declarations or stubs if the test needs them to compile. Confirm the test fails for the expected reason.
3. **GREEN.** Make the smallest change that passes the test and run the relevant checks. Preserve existing tests unless their underlying requirement has changed.
4. **Refactor.** Improve the implementation when needed while keeping the tests green.
5. **Commit.** Keep RED tests local. Once the applicable checks pass, commit the working implementation and its passing tests together before moving to the next milestone.

Several RED–GREEN cycles may belong to one milestone. A separate commit is not required for every test, file, import, or line change.

## Commit boundaries

Each commit should:

- Have one clear purpose that can be described briefly.
- Compile and pass the relevant tests and required checks.
- Include all necessary implementations and callers when a contract changes.
- Contain no intentionally failing tests or temporary no-op handlers presented as completed behavior.
- Be reviewable as a complete result, even when the larger feature is still in progress.

Later commits may depend on earlier commits. The same file may change across multiple commits. Every committed snapshot must remain consistent.

Possible milestones include:

| Milestone | Changes to commit together |
| --- | --- |
| Domain and persistence | A working use case, result types, repository contracts and implementations, necessary DAO changes, and tests |
| Presentation and UI | A complete screen interaction, prepared UI state, ViewModel behavior, required dependency wiring, and tests |
| A new business rule | The rule and its tests, plus all necessary changes in other layers to keep behavior consistent |

These are examples, not a requirement to split commits strictly by architectural layer. One logical change may span several layers.

A small feature may fit in one commit. For larger features, plan commit boundaries before implementation and revisit them as requirements evolve. Diff size and file count are review signals rather than rigid limits.

## Verification

Choose milestone checks based on the affected behavior, consumers, and CI requirements. Run the full project checks before opening a pull request and after substantial changes that require broader verification.

Before committing, inspect the final staged versions. Running tests in the working directory verifies the working tree, not an arbitrary staged subset. If splitting an accumulated change into intermediate commits, verify each intermediate snapshot separately.

Report which checks were actually run and their results. Do not claim successful verification for checks that were not executed.

### Desktop build before push

For every change that affects the desktop app, follow this order on macOS:

1. Run the relevant tests, ktlint, and detekt locally. Commit after all checks pass.
2. Immediately after committing, build the committed version from the repository root with `./gradlew :desktopApp:createDistributable`.
3. Push only after the build succeeds.

## Collaboration

Developers and coding assistants should identify suitable commit boundaries at completed GREEN milestones, suggest a concrete commit scope and message, and avoid carrying a long sequence of changes to the end of a large feature without intermediate commits.

Keep workflow documentation changes separate from feature implementation when they serve a different purpose.

The principle of separating logically distinct changes is also described in [Git's contribution guidelines](https://git-scm.com/docs/SubmittingPatches).
