package com.theunderseer.simplio.notifications

import kotlin.test.Test
import kotlin.test.assertEquals

class ReminderActivationQueueTest {
    @Test
    fun `multiple clicks before sign-in keep their order until accepted`() {
        val queue = ReminderActivationQueue()
        val account = reminderAccount("teacher")
        val first = ClassNotificationIdentity(account, "schedule:slot:20763")
        val second = ClassNotificationIdentity(account, "schedule:slot:20770")

        queue.enqueue(first)
        queue.enqueue(second)
        queue.enqueue(first)

        assertEquals(listOf(first, second), queue.pending.value)
        queue.acknowledge(first)
        assertEquals(listOf(second), queue.pending.value)
    }

    @Test
    fun `discarding another account leaves its own pending click intact`() {
        val queue = ReminderActivationQueue()
        val first = ClassNotificationIdentity(reminderAccount("first"), "schedule:slot:20763")
        val second = ClassNotificationIdentity(reminderAccount("second"), first.sessionId)

        queue.enqueue(first)
        queue.enqueue(second)
        queue.removeAccounts { it != second.account }

        assertEquals(listOf(second), queue.pending.value)
    }
}
