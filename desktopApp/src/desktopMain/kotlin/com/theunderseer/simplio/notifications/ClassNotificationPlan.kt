package com.theunderseer.simplio.notifications

import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

internal val wakeDeliveryGrace = 30.seconds

internal data class ClassNotification(val id: String, val title: String, val body: String, val endsAt: Instant) {
    val revision: String = sha256("$endsAt\n$title\n$body")
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
        removeDelivered = delivered - eligible.keys,
        schedule = upcoming.filter { pending[it.id] != it.revision },
        catchUp = overdue.filter { pending[it.id] != it.revision || now - it.endsAt >= wakeDeliveryGrace },
    )
}
