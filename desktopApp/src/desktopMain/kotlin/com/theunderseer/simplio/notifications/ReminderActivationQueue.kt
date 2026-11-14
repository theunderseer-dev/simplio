package com.theunderseer.simplio.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal class ReminderActivationQueue {
    private val queue = MutableStateFlow<List<ClassNotificationIdentity>>(emptyList())
    val pending = queue.asStateFlow()

    fun enqueue(identity: ClassNotificationIdentity) {
        queue.update { if (identity in it) it else it + identity }
    }

    fun acknowledge(identity: ClassNotificationIdentity) {
        queue.update { it - identity }
    }

    fun removeAccounts(shouldRemove: (String) -> Boolean) {
        queue.update { entries -> entries.filterNot { shouldRemove(it.account) } }
    }
}
