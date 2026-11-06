# Attendance reminders

Simplio asks macOS to show **Track attendance** when an eligible class ends.
The notification shows the schedule name and class date in the schedule's
time zone. For example, a Bangkok class ending at 10:15 becomes due at that
instant wherever the Mac is. Student names and account IDs are not included.

## Local eligibility

- Room projects class occurrences from each weekly schedule. Groups sharing
  a schedule produce one reminder for each class. Independent classes on the
  same date retain separate IDs and each uses its own end time.
- Active schedules need a linked group with at least one package purchased
  by the class date. Empty seeded groups and dates before the first
  registration do not produce reminders.
- A class with recorded attendance, a held decision, or a cancellation is
  ineligible. Legacy attendance uses the same group matching as Class History.
- The read is bounded to a maximum span of 31 days and requires no database
  migration. Room is the source for this read, including while offline.

## macOS delivery

In the packaged macOS app, open the three-dot menu next to **Add Package**
and turn on **Attendance reminders**. The app requests macOS permission for
alerts and sounds on first use. The setting is local to this Mac and to the
active Simplio account.

Reminders use the system notification sound. If alerts or sounds are blocked,
check **System Settings → Notifications** for the packaged app's name.
The current local package may appear as **com.theunderseer.simplio**.
Check **Play sound for notification** for that entry. After an update,
launch Simplio so it can replace upcoming silent requests with requests that
include sound. If macOS reports that sound is unavailable for an existing
installation, enabling reminders again requests alert and sound authorization.
macOS may retain the existing settings after that request.

The app places individual local requests in Apple's
[notification center][local] up to 14 days ahead, keeping at most 48 upcoming
requests at a time. macOS can deliver requests already queued even when
Simplio is quit or the network is unavailable. The rolling queue is updated
when the app starts, becomes active, wakes, or sees changes in Room. A
normal quit leaves requests in the system. Turning reminders off, losing an
active account, or switching accounts removes its requests.

On resume or launch, the app checks the last 14 days starting no earlier than
when reminders were enabled. Eligible missed classes are delivered separately.
A short grace period lets macOS deliver a pending request at wake before
Simplio retries it. Visible deliveries and delivery callbacks are recorded
locally to avoid duplicate reminders across restarts. A request dismissed
while Simplio was quit can only be deduplicated if macOS reports the delivery
or the dismissal. Focus and other system settings can affect presentation.
If the Mac was off and Simplio is not running after login, the catch-up check
waits until the next app launch. There is no login item or resident process.

Opening a notification restores the window and opens the existing attendance
dialog for its exact class once the matching account is ready. If sign-in is
required, it waits for sign-in. An existing dialog or draft is left intact
until it is closed. A class that has since been tracked, cancelled, or become
unavailable opens a message instead. If the app was quit while another
device changed attendance, an already queued alert may appear before the
change is synchronized to this Mac.

The native adapter is in `desktopApp`. iPhone delivery and a notification
action for cancelling a class are separate features. Firestore sync continues
to run only while the relevant app session is active.

## macOS verification

Build on macOS 13 or later with a JDK and the Xcode command-line tools. Run the generated
`.app` from Finder; a plain Gradle run is not a test of bundle identity or
Notification Center activation.

```bash
./gradlew :desktopApp:desktopTest --tests '*ClassNotificationPlanTest' \\
  :feature:attendance:desktopTest \\
  --tests '*RoomClassAttendanceReminderSourceTest' \\
  --tests '*AttendanceViewModelClassAttendanceTest' \\
  :desktopApp:ktlintCheck :feature:attendance:ktlintCheck \\
  :desktopApp:createDistributable
```

1. Open the app under `desktopApp/build/compose/binaries/main/app/`. Sign in
   and turn on **Attendance reminders**. Allow notifications in macOS.
2. Use a test schedule with registered students and two classes ending at
   different upcoming times. Confirm the first alert uses the first class
   date and opens that exact class. Check that entering attendance before
   the other class ends removes its pending alert.
   Check the system sound while Simplio is in the foreground.
3. Schedule another upcoming class and quit Simplio with **Cmd+Q**. Disable
   network access after scheduling. Verify macOS shows and sounds the queued alert,
   then restore the network if the existing sign-in flow requires it. Click
   the alert and confirm the correct class opens after sign-in.
4. Repeat across sleep with multiple untracked classes. Verify separate
   reminders, and verify that a restart or reopening the window does not
   repost known deliveries. Test denied permissions, turning reminders off,
   and clicking a stale alert after a class was already tracked.
5. With an attendance draft or another edit dialog open, click an alert.
   Check that the existing work remains and the target opens after closing it.

[local]: https://developer.apple.com/documentation/usernotifications/scheduling-a-notification-locally-from-your-app
