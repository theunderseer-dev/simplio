# iOS attendance

The iOS app opens the attendance list after Firebase email/password sign-in with a verified
account. Each student appears once with their latest purchase. The list shows the student or
pair name, used and total visits, expiration, and a disabled Check, Checked, or Renew control.
Refresh reads the current cloud data. The screen handles loading, empty accounts, access
denials, and retry.

## Boundaries

- `:iosApp` owns the SwiftUI app, Compose host, Firebase configuration, and the signed-in composition.
- `:shared` owns the existing Firebase sign-in and REST clients. The iOS HTTP engine uses Ktor Darwin.
- `:feature:attendance` owns the read-only session, Room import and queries, roster, and screen state.
  The desktop and iOS paths use the same verified snapshot import, latest-purchase use case,
  and Check/Renew calculations, including the schedule time zone and expiration boundary.

The first load and each user refresh read the account's Firestore head. A changed head triggers
the existing bounded, paginated snapshot read at one read time. The shared importer validates the
snapshot and applies it in a Room transaction. The list reads the imported rows through the same
repositories used on Desktop. See [Firestore pull](firestore-pull.md).

The iOS session opens an in-memory Room database for the signed-in account. Leaving the account
cancels its load and closes the database. Firebase sign-in and the snapshot live for the current
session. The roster uses the shared attendance clock to update date-dependent states while open.
The mobile screen is read-only and installs no cloud uploader.

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
./gradlew :feature:attendance:desktopTest --tests '*CloudAttendanceRosterTest' --tests '*AttendanceRosterScreenTest'
./gradlew :feature:attendance:iosSimulatorArm64Test --tests '*CloudAttendanceRosterTest' --tests '*AttendanceRosterScreenTest'
xcodebuild -project iosApp/iosApp.xcodeproj -scheme Simplio -configuration Debug \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

Run the signed-in screen in Xcode with a verified test account containing active, expired, and
prepaid purchases. Check scrolling, both appearance modes, and refresh after a Desktop change.
Check the same screen with an empty test account.

See [Apple lists and tables](https://developer.apple.com/design/human-interface-guidelines/lists-and-tables),
[Apple accessibility guidance](https://developer.apple.com/design/human-interface-guidelines/accessibility),
[running on iOS devices](https://developer.apple.com/documentation/xcode/running-your-app-on-simulated-or-physical-devices),
[Compose and SwiftUI integration](https://kotlinlang.org/docs/multiplatform/compose-swiftui-integration.html),
and [Android Developers on Room for KMP](https://developer.android.com/kotlin/multiplatform/room).
