package com.theunderseer.simplio.notifications

import java.security.MessageDigest
import java.util.HexFormat

private const val NOTIFICATION_PREFIX = "simplio.attendance."

internal data class ClassNotificationIdentity(val account: String, val sessionId: String) {
    val id: String get() = "$NOTIFICATION_PREFIX$account/$sessionId"

    companion object {
        fun parse(id: String): ClassNotificationIdentity? {
            if (!id.startsWith(NOTIFICATION_PREFIX)) return null
            val payload = id.removePrefix(NOTIFICATION_PREFIX)
            val account = payload.substringBefore('/')
            val sessionId = payload.substringAfter('/', missingDelimiterValue = "")
            if (account.length != 64 || account.any { it !in '0'..'9' && it !in 'a'..'f' }) return null
            if (sessionId.isEmpty() || sessionId.length > 280 || '/' in sessionId) return null
            return ClassNotificationIdentity(account, sessionId)
        }
    }
}

internal fun reminderAccount(userId: String?): String = sha256(if (userId == null) "local" else "firebase:$userId")

internal fun sha256(value: String): String =
    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)))
