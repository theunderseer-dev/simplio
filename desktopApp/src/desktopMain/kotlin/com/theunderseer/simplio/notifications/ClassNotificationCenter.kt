package com.theunderseer.simplio.notifications

import kotlinx.coroutines.flow.Flow

internal enum class NotificationAuthorization { NOT_DETERMINED, DENIED, ALLOWED }

internal sealed interface ClassNotificationEvent {
    data object Refresh : ClassNotificationEvent
    data class Observed(val id: String, val open: Boolean) : ClassNotificationEvent
}

internal interface ClassNotificationCenter {
    val events: Flow<ClassNotificationEvent>
    suspend fun authorization(): NotificationAuthorization
    suspend fun requestAuthorization(): NotificationAuthorization
    suspend fun pending(): Map<String, String>
    suspend fun delivered(): Set<String>
    suspend fun remove(pending: Set<String>, delivered: Set<String>)
    suspend fun add(notification: ClassNotification, immediately: Boolean)
}
