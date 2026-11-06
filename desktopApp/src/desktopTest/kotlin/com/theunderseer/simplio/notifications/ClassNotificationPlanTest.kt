package com.theunderseer.simplio.notifications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ClassNotificationPlanTest {
    private val now = Instant.parse("2026-11-06T03:15:00Z")

    @Test
    fun `schedules each class at its own end and catches up at the boundary`() {
        val due = notification("due")
        val early = notification("early", now + 15.minutes)
        val late = notification("late", now + 45.minutes)
        val plan = planClassNotifications(listOf(late, due, early), emptyMap(), emptySet(), emptySet(), now)
        assertEquals(listOf(early, late), plan.schedule)
        assertEquals(listOf(due), plan.catchUp)
    }

    @Test
    fun `keeps pending delivery across wake and catches up only missed classes`() {
        val pending = notification("pending", now - 10.seconds)
        val observed = notification("observed", now - 2.minutes)
        val delivered = notification("delivered", now - 1.minutes)
        val missed = notification("missed", now - 4.minutes)
        val alsoMissed = notification("also-missed", now - 5.minutes)
        val plan = planClassNotifications(
            candidates = listOf(pending, observed, delivered, missed, alsoMissed),
            pending = mapOf(pending.id to pending.revision, missed.id to missed.revision),
            delivered = setOf(delivered.id),
            acknowledged = setOf(observed.id),
            now = now,
        )
        assertEquals(listOf(alsoMissed, missed), plan.catchUp)
        assertEquals(emptySet(), plan.removePending)
        assertEquals(emptySet(), plan.removeDelivered)
    }

    @Test
    fun `removes ineligible requests and replaces a changed time under the same ID`() {
        val original = notification("moved", now + 15.minutes)
        val moved = original.copy(endsAt = now + 45.minutes)
        val recorded = notification("recorded", now + 30.minutes)
        val cancelled = notification("cancelled", now - 15.minutes)
        val plan = planClassNotifications(
            candidates = listOf(moved),
            pending = mapOf(original.id to original.revision, recorded.id to recorded.revision),
            delivered = setOf(cancelled.id),
            acknowledged = emptySet(),
            now = now,
        )
        assertEquals(setOf(recorded.id), plan.removePending)
        assertEquals(setOf(cancelled.id), plan.removeDelivered)
        assertEquals(listOf(moved), plan.schedule)
        assertNotEquals(original.revision, moved.revision)
    }

    @Test
    fun `upgrades upcoming silent requests and gives overdue requests the wake grace period`() {
        val upcoming = notification("upcoming", now + 15.minutes)
        val overdue = notification("overdue", now - 10.seconds)
        val delivered = notification("delivered", now - 20.seconds)
        val candidates = listOf(upcoming, overdue, delivered)
        val pending = candidates.associate { it.id to sha256("${it.endsAt}\n${it.title}\n${it.body}") }

        val plan = planClassNotifications(candidates, pending, setOf(delivered.id), emptySet(), now)

        assertEquals(listOf(upcoming), plan.schedule)
        assertEquals(emptyList(), plan.catchUp)
        assertEquals(setOf(delivered.id), plan.removePending)
        assertEquals(emptySet(), plan.removeDelivered)
    }

    @Test
    fun `a class notification is scoped to one account and retains its canonical session ID`() {
        val sessionId = "schedule:slot:20763"
        val local = ClassNotificationIdentity(reminderAccount(null), sessionId)
        val signedIn = ClassNotificationIdentity(reminderAccount("test-user"), sessionId)
        assertNotEquals(local.id, signedIn.id)
        assertEquals(signedIn, ClassNotificationIdentity.parse(signedIn.id))
        assertNull(ClassNotificationIdentity.parse("unrelated-notification"))
    }

    private fun notification(id: String, endsAt: Instant = now) = ClassNotification(
        id = id,
        title = "Track attendance",
        body = "Weekly schedule",
        endsAt = endsAt,
    )
}
