# Development workflow

Build features through small, complete milestones. Commit each verified milestone rather than accumulating every change until the entire feature is finished.

## TDD cycle

1. Define the next milestone, its expected behavior, relevant checks, and intended commit boundary.
2. **RED:** add a test for the required behavior. Minimal declarations or temporary stubs may be needed to compile the test; verify that it fails for the expected reason.
3. **GREEN:** implement the behavior and run the relevant checks. Preserve existing tests unless their underlying requirement has changed.
4. **Refactor:** improve the implementation when needed while keeping the tests green.
5. **Commit:** once the milestone is complete and verified, commit it before moving to the next milestone.

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

## Collaboration

Developers and coding assistants should identify suitable commit boundaries at completed GREEN milestones, suggest a concrete commit scope and message, and avoid carrying a long sequence of changes to the end of a large feature without intermediate commits.

Keep workflow documentation changes separate from feature implementation when they serve a different purpose.

The principle of separating logically distinct changes is also described in [Git's contribution guidelines](https://git-scm.com/docs/SubmittingPatches).
