# Custom package templates

`PackageType` describes a teacher's reusable purchase terms: display name, credit capacity,
whole-unit price, optional validity in weeks, and a maximum of one or two credits per attendance.
It has no purchase date, activation date, schedule, or student owner.

`StudentPackage` stores a snapshot of those terms when a student buys or renews a package.
It owns the purchase date and attendance history. The first recorded attendance activates it.
Changing a catalog template later must not modify an existing purchase snapshot.

## Expiration and credits

- A positive `validityWeeks` creates an expiration date relative to activation, independently of capacity.
- A null `validityWeeks` means no calendar expiration, including after activation. It is not zero,
  a missing cloud field, or an artificial far-future date.
- A package with no expiration is completed when its attendance credits reach capacity.
- Removing all attendance clears activation. The optional validity policy remains part of the purchase.
- A missed, untracked, or cancelled date does not deduct credits. Only recorded attendance does.
- One-credit private attendance can represent a couple; this feature does not track a separate headcount.
- A two-credit template uses the existing one/two-person attendance selection.

## Relationship with schedules

The student belongs to a group and the group references its schedule. The schedule supplies eligible
attendance dates; it does not preallocate package credits or finish when one student's package ends.
For example, an eight-credit package can be used on a three-times-weekly schedule without requiring
its capacity to be divisible by three.

Different frequency-based prices are separate templates with descriptive names. This MVP does not
infer pricing from a schedule or enforce a tariff's weekly frequency. Cancellation, rescheduling,
freezing validity, catalog editing and archival are separate features.

## Creation and synchronization

`Add Package` follows `Add Group`. The scrollable dialog requires a name, positive credit capacity,
and nonnegative whole-unit price. `No expiration` hides the required positive week input.
Names are compared ignoring case and repeated surrounding/internal whitespace.
Numeric input is validated without silently truncating overflowing or fractional amounts.

`CreatePackageTypeUseCase` validates the request. The Room repository revalidates it and checks the
name inside the same transaction that inserts the template and creates the existing outbox record.
Successful local creation refreshes package choices for registration and renewal without waiting
for a network request. Duplicate or failed creation keeps the dialog and attendance screen available.

Two devices can independently create templates with the same name. The existing sync review requires
a new name for the local template and preserves both templates and purchase snapshots. It does not
silently merge different prices under one template.

## Storage and wire compatibility

Room migration 12 to 13 makes validity nullable in both `package_types` and `student_packages`.
Dependent attendance rows, session identity, catalog/purchase terms, sync records and AUTOINCREMENT
high-water marks are preserved. Historical exported schemas remain unchanged; schema 13 is generated
by Room.

Firestore protocol version 4 advertises support for optional validity. Finite legacy documents remain
readable. A no-expiration template or purchase uses document schema version 3 and an explicit Firestore
`nullValue` for `validityWeeks`; missing or malformed validity is rejected before replacing local rows.
Schedule/session documents continue using their existing schema version 2.

All devices sharing an account should use the new version before synchronizing after this upgrade.
Older clients do not understand protocol 4 and must not reinterpret an unlimited purchase as finite.
Previously frozen uploads retain their original payload for idempotent retry.

## Scope of tests

- `commonTest`: canonical private templates, validation, concurrent duplicate saves, local state,
  credit completion, finite/unlimited rules, UI input, sync round trips and catalog name conflicts.
- `desktopTest`: migration from the historical schema and Firestore REST round trip in the emulator.
- Manual macOS verification: window resizing, keyboard focus, full form visibility and package selection
  during registration and renewal.
