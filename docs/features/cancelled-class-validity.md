# Cancelled class: package validity

## Business rules

- Only an explicit new Cancel class action grants compensation. Unrecorded classes, HELD with no visits, opening history and elapsed time do not change packages.
- Cancellation remains available only when neither saved nor draft attendance exists. Existing stale-snapshot checks apply before any writes.
- The roster contains current members of every group assigned to this schedule. There is still one group per student; historical group membership is not reconstructed.
- For each student, consider the latest package purchased on or before the cancelled class date, as in the class roster. A later renewal is never extended instead of that historical purchase.
- A package qualifies when it has a finite validity, its first attendance and purchase are on or before the class date, its compensated expiration is on or after that date, and credits consumed through that date are below its purchased capacity. Later attendance may have completed the package by today; eligibility is still evaluated on the class date.
- Packages with no expiration or no first attendance, packages already exhausted on that date, and packages expired before that date receive no grant.
- Move the current expiration to the next distinct date of the cancelled schedule's weekly pattern: Friday to Monday adds three days; Monday to Friday adds four. Multiple time slots on one day still count as one date, consistent with the existing attendance-per-date contract.
- Use the schedule's time zone and valid wall times. Projection covers at most the next fourteen days, including a skipped weekly occurrence during a DST gap. Series start/end boundaries do not cap purchased validity compensation; they continue to control which occurrences can be tracked.
- Repeated cancellations move the already extended expiration forward again. Do not separately skip other cancelled future dates: each explicit cancellation contributes exactly one shift.
- Purchased capacity, attendance counts, remaining credits, price, purchase date and activation remain unchanged. Package history and renewal availability read the compensated expiration from StudentPackage.
- Saved cancellations from earlier releases receive no automatic backfill. There is no new reopening, undo, editing or rescheduling action in this change.

## Persistence and synchronization

- Room version 16 adds `student_packages.validityExtensions`, a JSON map from stable session ID to positive day offset. The map belongs to the purchased package, not the catalog template. Missing legacy grants migrate to an empty map. Historical schema exports remain unchanged.
- Each cancellation identity is awarded once. If correcting the first attendance changes activation, recompute the existing awarded shifts against the new base expiration in historical class order; do not award or revoke cancellation identities. Clearing all visits hides expiration until reactivation. The class decision, package updates and durable outbox entries commit under one Room writer transaction. Failure rolls back all of them; resubmitting a finalized class cannot award twice.
- The converter stores one small value object; no independent grant lifecycle or extra cloud collection is needed. Each grant retains its cancellation identity so upload and conflict grouping can follow the dependency.
- Firestore protocol 7 supports student-package document schema 4 with `validityExtensions` as a mapValue. Package documents without grants retain their prior schema. Legacy documents import with no grants; malformed, downgraded or orphan grants reject the entire snapshot before replacement. Every referenced session must exist as CANCELLED.
- A class upload unit includes every pending package with a grant for that class, and the reciprocal pending class dependencies of a changed package. The 400-business-document / 8 MiB atomic limit remains; oversized units pause rather than publish partial compensation.
- Pull restores stored grants, never reapplies cancellation logic, and creates no echo upload. Repeat pull and upload receipt recovery cannot add days again.
- Conflict review selects each affected student's complete package/attendance graph together with the class decision, in either direction. Cancellation versus attendance remains an explicit choice. It never combines a cancelled class with the other side's uncompensated package. Conflicting independent offline changes still use the existing review policy rather than automatic arithmetic merging.
- Upgrade all devices for the account before using protocol 7. Older clients reject the newer head; existing frozen protocol 1–6 requests retain their original body and receipt semantics.

## Validation

`commonTest` covers expiration shifts, eligibility on the historical date, shared groups, exclusions, repeated and out-of-order cancellations, unchanged credits, atomic upload, repeated pull, invalid cloud graphs and cancellation/attendance conflict choices.

`desktopTest` validates migration 15→16 against exported schemas, preserves purchases, attendance, existing decisions and queued mutations, and verifies grants survive reopening the database.

These application rules build on Android's [Room migration guidance](https://developer.android.com/training/data-storage/room/migrating-db-versions) and Firestore's [atomic write guidance](https://firebase.google.com/docs/firestore/manage-data/transactions). Those documents do not define this application's cancellation policy.
