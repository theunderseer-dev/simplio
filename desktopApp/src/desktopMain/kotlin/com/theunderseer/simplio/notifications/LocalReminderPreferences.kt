package com.theunderseer.simplio.notifications

import java.util.prefs.Preferences
import kotlin.time.Instant

internal class LocalReminderPreferences {
    private val root = Preferences.userRoot().node("com/theunderseer/simplio/attendance-reminders")

    fun enabledSince(account: String): Instant? = root.node(account).getLong("enabledSince", Long.MIN_VALUE)
        .takeUnless { it == Long.MIN_VALUE }?.let(Instant::fromEpochMilliseconds)

    fun setEnabledSince(account: String, instant: Instant?) {
        val node = root.node(account)
        if (instant == null) {
            node.remove("enabledSince")
        } else {
            node.putLong("enabledSince", instant.toEpochMilliseconds())
        }
        node.flush()
    }

    fun acknowledged(account: String, candidates: Set<String>, since: Instant): Set<String> {
        val node = root.node(account).node("receipts")
        val cutoff = since.toEpochMilliseconds()
        val result = candidates.filterTo(mutableSetOf()) { node.getLong(sha256(it), Long.MIN_VALUE) >= cutoff }
        node.keys().filter { node.getLong(it, Long.MIN_VALUE) < cutoff }.forEach(node::remove)
        return result
    }

    fun remember(account: String, ids: Set<String>, now: Instant) {
        if (ids.isEmpty()) return
        val node = root.node(account).node("receipts")
        ids.forEach { node.putLong(sha256(it), now.toEpochMilliseconds()) }
        node.flush()
    }
}
