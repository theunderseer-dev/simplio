# iOS attendance

The iOS app opens the attendance list after Firebase email/password sign-in with a verified
account. Each student appears once with their latest purchase. The list shows the student or
pair name, used and total visits, expiration, and the Check, Checked, or Renew action.
The list uses the same student group and row order as Desktop. Empty groups are hidden.
The iOS roster reuses the Desktop action icons and highlights renewal rows in the same green.
Check opens the same schedule-aware calendar as Desktop. Checked opens the removal confirmation.
Renew uses the shared purchase form with an existing package template, an optional first
attendance, and an optional note. The compact Purchase date field opens a calendar with a
keyboard-input option. Room changes update the list after each save.

The round add button shows Add student, Add package, and Add schedule from top to bottom.
Schedule appears first, closest to the trigger, followed by Package and Student as the menu
expands upward. With Reduce Motion enabled the actions fade in together without moving.
The buttons use a thin outline that fades in with the button surface.
The roster keeps its usual appearance while the menu is open. Tapping outside or on the
trigger closes it.

Each action opens the same form used on Desktop. Add schedule creates a student group from a
new or existing weekly schedule. Add package creates a reusable package template. Add student
registers a student, saves the purchase, and optionally records the first attendance. A
successful save updates the roster and selections from Room. The first student in a new group
uses the group's schedule and venue time zone. Failed saves keep the form open.
Date and time pickers replace the form while open. Confirming or cancelling a picker
returns to the same form draft.

The iOS Compose host paints the theme to the screen edges. Attendance content observes
the status bar and cutout, and the last row and add button clear the Home indicator.
The keyboard changes the layout of the active form while the underlying roster stays fixed.

## Boundaries

- `:iosApp` owns the SwiftUI app, Compose host, Firebase configuration, iOS Keychain adapter, and
  account database paths.
- `:shared` owns the Firebase sign-in screen, session manager, storage contract, and REST clients.
  The iOS HTTP engine uses Ktor Darwin.
- `:feature:attendance` owns the session, Room import and queries, roster, and screen state.
  Desktop and iOS share the dependency graph, date rules, dialogs, and use cases for creation
  and attendance. The mobile roster and quick actions use Compose in `iosMain`.

The shared coordinator checks the Firestore head at sign-in, on Refresh, and while the device is
idle. It uses the same bounded snapshot import and durable outbox as Desktop. A save checks the
reviewed purchase in a Room transaction. The use case updates the visit or purchase and journal
together. Sync retries and conflict review use the shared status UI. See [Firestore pull](firestore-pull.md)
and [Student package renewal](package-renewal.md).

The iOS app keeps the Room database under its Application Support directory with a path derived
from the Firebase project ID and account UID. Leaving the account cancels list and sync work
and closes the database. Saved changes and pending uploads survive closing the app or signing out.
The roster uses the shared attendance clock to update date-dependent states while open.

## Remember me

The shared sign-in screen offers Remember me as an opt-in. On iOS, the app stores the Firebase UID
and refresh token in an app-private Keychain item scoped to the Firebase project. The item is
available while the device is unlocked and stays on that device. The user's password is used only
for the interactive sign-in.

At launch, the shared session manager exchanges the refresh token for a fresh ID token, confirms the
same UID, and rechecks email verification and Firestore access before opening attendance. An
unchanged refresh token stays in Keychain without a write. If Keychain is temporarily unavailable
while saving a new token, the verified session stays active in memory. Saving can be retried when
the app becomes active or the device unlocks.

Temporary Keychain failures preserve the saved session and offer Retry saved sign-in. The app also
retries a temporarily unavailable Keychain read when it becomes active or protected data becomes
available. A rejected refresh token requires password sign-in.
Keychain errors include the operation and OSStatus when available.
Sign out removes the saved item before leaving the account. Sign-in with Remember me off also
clears an older saved session.

See [Apple Keychain item accessibility](https://developer.apple.com/documentation/security/restricting-keychain-item-accessibility)
and [Firebase session management](https://firebase.google.com/docs/auth/admin/manage-sessions).

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

The Keychain test needs a booted simulator. Get its ID with `xcrun simctl list devices` and use
that ID in the commands below.

```bash
device_id="your-simulator-udid"
xcrun simctl bootstatus "$device_id" -b
./gradlew :iosApp:iosSimulatorArm64Test --device "$device_id" --tests '*IosFirebaseSessionStoreTest'
./gradlew :feature:attendance:iosSimulatorArm64Test --device "$device_id" --tests '*AttendanceRoster*Test'
xcodebuild -project iosApp/iosApp.xcodeproj -scheme Simplio -configuration Debug \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

On a signed iPhone build, sign in with Remember me checked, terminate the app, and reopen it.
Verify that attendance opens for the same test account. Sign out, reopen the app, and verify that
the sign-in screen stays visible. Repeat with Remember me unchecked. To verify recovery, try
relaunching a remembered session offline, reconnect, and tap Retry saved sign-in. With Remember me
enabled, also lock the device during startup and verify that the session restores after unlocking
and returning to Simplio.

Run the signed-in screen with a verified test account containing active, expired, and prepaid
purchases. Check a class, confirm that the counter updates, and remove the visit with Checked.
Renew with and without first attendance and verify the purchase, note, and visit count on Desktop
after sync. Check both appearance modes and an empty account.
Open the add menu on a populated and empty roster. Verify that the buttons fit above the home
indicator, that tapping outside closes the menu, and that Reduce Motion disables the slide.
Create a schedule and a package on iOS, then use both to add the first student in the new group.
Verify that the roster updates locally and Desktop sees the new records after sync.
While registering a student, tap Full name, enter a name, select a group, and open
Registration date with the keyboard still visible. Cancel and reopen the picker, then choose
a date and verify the form still has the entered name and selected group. Watch that the
roster behind the form remains fixed. Repeat for schedule date and time pickers.

See [Apple lists and tables](https://developer.apple.com/design/human-interface-guidelines/lists-and-tables),
[Apple accessibility guidance](https://developer.apple.com/design/human-interface-guidelines/accessibility),
[Apple motion guidance](https://developer.apple.com/design/human-interface-guidelines/motion),
[Apple date picker guidance](https://developer.apple.com/design/human-interface-guidelines/pickers),
[running on iOS devices](https://developer.apple.com/documentation/xcode/running-your-app-on-simulated-or-physical-devices),
[Compose and SwiftUI integration](https://kotlinlang.org/docs/multiplatform/compose-swiftui-integration.html),
and [Android Developers on Room for KMP](https://developer.android.com/kotlin/multiplatform/room).
