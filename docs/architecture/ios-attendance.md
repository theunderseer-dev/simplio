# iOS attendance

The iOS app opens the attendance list after Firebase email/password sign-in with a verified
account. Each student appears once with their latest purchase. The list shows the student or
pair name, used and total visits, expiration, and the Check, Checked, or Renew action.
Check opens the same schedule-aware calendar as Desktop. Checked opens the removal confirmation.
Renew uses the shared purchase form with an existing package template, an optional first
attendance, and an optional note. The compact Purchase date field opens a calendar with a
keyboard-input option. Room changes update the list after each save.

## Boundaries

- `:iosApp` owns the SwiftUI app, Compose host, Firebase configuration, and account database paths.
- `:shared` owns the existing Firebase sign-in and REST clients. The iOS HTTP engine uses Ktor Darwin.
- `:feature:attendance` owns the session, Room import and queries, roster, and screen state.
  Desktop and iOS share the latest-purchase use case, date rules, dialogs, and use cases
  for attendance, removal, and renewal.

The shared coordinator checks the Firestore head at sign-in, on Refresh, and while the device is
idle. It uses the same bounded snapshot import and durable outbox as Desktop. A save checks the
reviewed purchase in a Room transaction. The use case updates the visit or purchase and journal
together. Sync retries and conflict review use the shared status UI. See [Firestore pull](firestore-pull.md)
and [Student package renewal](package-renewal.md).

The iOS app keeps the Room database under its Application Support directory with a path derived
from the Firebase project ID and account UID. Leaving the account cancels list and sync work
and closes the database. Saved changes and pending uploads survive closing the app or signing out.
The roster uses the shared attendance clock to update date-dependent states while open.

## Xcode setup

Use a Mac with Xcode, JDK 21, and an iOS 17 or newer device or simulator. Set up Xcode once and
install an iOS Simulator runtime from Xcode Settings if you plan to run on the simulator.
The supported simulator architecture is arm64 on Apple silicon.
Open `iosApp/iosApp.xcodeproj` and select the shared `Simplio` scheme. The Xcode build phase
builds the Kotlin framework through Gradle. For a physical iPhone, select your development team
in Signing & Capabilities, select the connected device, and run the scheme. Xcode handles
development signing.

For cloud access, create the ignored file `iosApp/Firebase.local.xcconfig` using the Firebase
project ID and client API key of the test environment, then rebuild:

```xcconfig
FIREBASE_PROJECT_ID = your-project-id
FIREBASE_API_KEY = your-client-api-key
```

Use a verified test account authorized to read attendance in that project.

## Local verification

If the active command-line tools point to Command Line Tools, set `DEVELOPER_DIR` in your
terminal to the full Xcode developer directory, such as
`/Applications/Xcode.app/Contents/Developer`.

```bash
./gradlew :feature:attendance:desktopTest --tests '*AttendanceRoster*Test'
./gradlew :feature:attendance:iosSimulatorArm64Test --tests '*AttendanceRoster*Test'
xcodebuild -project iosApp/iosApp.xcodeproj -scheme Simplio -configuration Debug \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

Run the signed-in screen with a verified test account containing active, expired, and prepaid
purchases. Check a class, confirm that the counter updates, and remove the visit with Checked.
Renew with and without first attendance and verify the purchase, note, and visit count on Desktop
after sync. Check both appearance modes and an empty account.

See [Apple lists and tables](https://developer.apple.com/design/human-interface-guidelines/lists-and-tables),
[Apple accessibility guidance](https://developer.apple.com/design/human-interface-guidelines/accessibility),
[Apple date picker guidance](https://developer.apple.com/design/human-interface-guidelines/pickers),
[running on iOS devices](https://developer.apple.com/documentation/xcode/running-your-app-on-simulated-or-physical-devices),
[Compose and SwiftUI integration](https://kotlinlang.org/docs/multiplatform/compose-swiftui-integration.html),
and [Android Developers on Room for KMP](https://developer.android.com/kotlin/multiplatform/room).
