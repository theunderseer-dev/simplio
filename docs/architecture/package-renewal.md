# Student package renewal

## Scope and baseline

This feature starts from main after the sync-conflicts change (Room schema 10). Offline session
restoration remains deferred and is not included in this branch. The feature creates another purchase
for an existing student; it does not register another student or modify their group.

## User flow

- A completed or expired package displays the same ♻️ marker used by attendance sharing in place of
  the check action. Active and unactivated packages retain their existing attendance action.
- The renewal dialog shows the student name and current group. It preselects the current package type
  from the available catalog and the current date. The user can choose another package or purchase
  date, include the first attendance, choose the duo visit count, and review their existing note.
- Registration and renewal share `StudentPackageDialog`. Expiration preview appears only when the
  first attendance is checked. Note remains the last editable field and can be cleared explicitly.
- Saving disables confirmation and dismissal. An error stays in the dialog with the entered values;
  success refreshes the row, closes the dialog and restores the ordinary attendance action.
- Cancelling an unsaved dialog has no effect. Undoing a saved purchase is outside this MVP. Removing
  the first attendance of the new package leaves the new purchase present and awaiting activation;
  it does not revert the student to their previous package.

## Domain and persistence

`RenewStudentPackageUseCase` takes the ID of the package that the user reviewed. The transaction
checks that it is still the latest package, checks eligibility and visit count, updates the reviewed
student note, and creates a separate purchase with optional initial attendance. A stale confirmation
returns `PackageChanged`, including when the newer package is already completed. This prevents retries
from creating duplicate purchases. The ViewModel additionally guards repeated clicks while saving.

The earliest renewal date is no earlier than the previous purchase and its last recorded attendance.
For an expired incomplete package it is also after the inclusive expiration date. This keeps purchase
ordering coherent with the repository's `(purchasedOn, id)` latest-package query. The dialog also
rejects future dates. A completed package can be renewed on its last attendance date, for example
when a student pays for the next package after class.

Without initial attendance the new package has zero used visits and null activation/expiration.
With attendance it starts on the selected purchase date and records the selected visit count. The
previous package, its purchase terms and its attendance remain unchanged. Class history continues to
query attendance across purchases.

The existing Room journal records note, purchase and attendance changes inside the same enclosing
transaction. A later failure rolls them all back, including the sync queue. Existing aggregate upload,
head preconditions and conflict review handle the new purchase; there is no separate cloud-write path
and no Firestore protocol or Room schema change.

## Verification locations

- `feature/attendance/src/commonTest/.../domain/usecase/RenewStudentPackageOptionsTest.kt`: optional
  first attendance, duo counts, note review, date boundaries, stale confirmations and retries.
- `feature/attendance/src/commonTest/.../presentation/viewmodel/AttendanceViewModelRenewalTest.kt`:
  row refresh, inline failures, repeated confirmation, and removing a renewed package's first visit.
- `feature/attendance/src/commonTest/.../presentation/screen/AttendanceScreenPackageRenewalUiTest.kt`:
  4/4, 5/5 and 8/8 actions, expired packages, prefilled form, calendar, saving and cancellation.
- `feature/attendance/src/desktopTest/.../domain/usecase/RenewStudentPackagePersistenceTest.kt`:
  real database reopening, old class history and transactional rollback of the sync queue.
- `feature/attendance/src/desktopTest/.../data/remote/FirestorePackageRenewalEmulatorTest.kt`:
  two-database round trips with and without initial attendance, and competing renewals.

Emulator tests use demo accounts and emulator owner credentials. They do not deploy or validate
production Firestore Rules. Real authentication and offline session restoration are unchanged.
