package com.theunderseer.simplio.notifications

import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

internal val wakeDeliveryGrace = 30.seconds
private const val SOUND_REVISION_PREFIX = "sound:"

internal data class ClassNotification(
    val id: String,
    val title: String,
    val body: String,
    val endsAt: Instant,
) {
    val revision: String = SOUND_REVISION_PREFIX + sha256("$endsAt\n$title\n$body")
}

internal data class ClassNotificationPlan(
    val removePending: Set<String>,
    val removeDelivered: Set<String>,
    val schedule: List<ClassNotification>,
    val catchUp: List<ClassNotification>,
)

internal fun planClassNotifications(
    candidates: List<ClassNotification>,
    pending: Map<String, String>,
    delivered: Set<String>,
    acknowledged: Set<String>,
    now: Instant,
): ClassNotificationPlan {
    val eligible = candidates.associateBy { it.id }
    val notified = delivered + acknowledged
    val ordered = eligible.values.sortedWith(compareBy<ClassNotification> { it.endsAt }.thenBy { it.id })
    val upcoming = ordered.filter { it.endsAt > now && it.id !in notified }.take(48)
    // An overdue request can still be in the OS queue during wake. Let the system finish delivering it.
    val overdue = ordered.filter { it.endsAt <= now && it.id !in notified }
    val keep = (upcoming + overdue).mapTo(mutableSetOf()) { it.id }
    return ClassNotificationPlan(
        removePending = pending.keys - keep,
        // A pending request can move to Notification Center between the two reads and removal.
        removeDelivered = (pending.keys + delivered) - eligible.keys,
        schedule = upcoming.filter { pending[it.id] != it.revision },
        catchUp = overdue.filter { notification ->
            val queuedRevision = pending[notification.id]
            // A legacy silent request can still be delivered by macOS during wake.
            val matchingRequest = queuedRevision == notification.revision ||
                queuedRevision == notification.revision.removePrefix(SOUND_REVISION_PREFIX)
            !matchingRequest || now - notification.endsAt >= wakeDeliveryGrace
        },
    )
}
