# Attendance reminders

A class becomes due at its scheduled **end** in the schedule's time zone.
For a class ending at 10:15 in Bangkok, the reminder becomes due at that
instant, wherever the Mac is. Show **Track attendance** with the schedule
name and the class day and date. Do not include student names in the notification.

## Local eligibility

- Project class occurrences from Room using the existing schedule calculator.
  Keep the canonical session ID, so groups sharing a class produce one reminder
  and two independent classes on the same date stay distinct.
- Use one bounded snapshot for upcoming classes and missed classes. The
  snapshot window is exclusive at the lower end and inclusive at the upper
  end, with a maximum span of 31 days. This is a query bound; it does not change
  which classes can be tracked in Class History.
- Include active schedules with a linked group that has at least one package
  purchased by the class date. This avoids reminders for empty seeded groups
  and for dates before anyone was registered.
- Suppress a class when the teacher already recorded attendance or marked it
  held or cancelled. Match legacy attendance with the same group rule as
  Class History. Respect the existing schedule bounds and DST rules.
- No extra database table or cloud changes are needed for this read. It is
  scoped to the Room database for the active account and works offline.

## macOS delivery follow-up

Keep eligibility in `feature/attendance` and implement the adapter in
`desktopApp`. Use Apple's [local notifications][local] from the packaged
Simplio application. Ask for permission when the teacher enables reminders.
Schedule individual requests for a rolling window, initially 14 days ahead,
rather than repeating alerts for every weekday indefinitely. The OS delivers
queued requests even when the app is not running. Refresh the queue on
launch, resume, and local changes to the affected tables, including synced
changes applied to Room. This does not require a permanent process or login item.

On wake or launch, reconcile due reminders in a recent catch-up window,
initially 14 days and no earlier than when reminders were enabled. Deliver
each still eligible missed class separately. Reconcile pending and delivered
requests by account and session ID; store acknowledgements on this Mac so
restart or repeated wake does not repost a known delivered reminder. Verify
dismissal and sleep behavior on macOS before relying on those signals for
deduplication. When the app was closed and could not observe an edit on
another device, a queued reminder may be stale: recheck local eligibility
before opening the class. If the Mac was off, do not promise an alert at its
original time, or on unlock if Simplio has no queued request and is not running.

The notification click should open the existing class tracking UI, including
from a cold launch, once the correct account database is ready. Revalidate the
session by ID without depending on the visible history page. If it is already
tracked, cancelled, or no longer available, open Class History and explain why.
Clear the previous account's requests when explicitly signing out or switching
accounts. Keep cancellation inside the existing confirmation flow; consider
adding a shortcut after the main click flow is proven.

iPhone mirroring is documented for iPhone-to-Mac notifications. The iPhone
follow-up needs its own client or a separate delivery channel; it is outside
this change. Firestore sync remains independent and currently runs while
the attendance session in the app is active.

Before enabling delivery, verify permission granted and denied, foreground and
fully quit, offline operation, screen lock and sleep across two due classes,
cold and warm notification clicks, and cancellation or attendance while a
request is pending. Test a packaged `.app`, not just the Gradle run task.

[local]: https://developer.apple.com/documentation/usernotifications/scheduling-a-notification-locally-from-your-app
