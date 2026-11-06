package com.theunderseer.simplio.notifications

import com.theunderseer.simplio.desktop.resources.Res
import com.theunderseer.simplio.desktop.resources.attendance_reminder_body
import com.theunderseer.simplio.desktop.resources.attendance_reminder_title
import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomClassAttendanceReminderSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.toJavaLocalDate
import org.jetbrains.compose.resources.getString
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

internal data class ReminderActivation(val identity: ClassNotificationIdentity, val sequence: Long)

internal data class AttendanceReminderSettings(
    val account: String? = null,
    val enabled: Boolean = false,
    val permissionDenied: Boolean = false,
    val failed: Boolean = false,
)

internal class DesktopAttendanceReminders(
    private val center: ClassNotificationCenter,
    private val diagnostics: Boolean,
) {
    private val mutex = Mutex()
    private val preferences = LocalReminderPreferences()
    private var activeAccount: String? = null
    private val refresh = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val mutableActivation = MutableStateFlow<ReminderActivation?>(null)
    private val mutableSettings = MutableStateFlow(AttendanceReminderSettings())

    val activation = mutableActivation.asStateFlow()
    val settings = mutableSettings.asStateFlow()

    suspend fun observeNativeEvents(onOpen: () -> Unit) {
        var sequence = 0L
        center.events.collect { event ->
            when (event) {
                ClassNotificationEvent.Refresh -> refresh.tryEmit(Unit)
                is ClassNotificationEvent.Observed -> {
                    val identity = ClassNotificationIdentity.parse(event.id) ?: return@collect
                    trace("native callback: ${if (event.open) "opened" else "observed"}")
                    if (event.open) {
                        onOpen()
                        mutableActivation.value = ReminderActivation(identity, ++sequence)
                    }
                    guarded {
                        preferences.remember(identity.account, setOf(event.id), Clock.System.now())
                    }
                }
            }
        }
    }

    fun acknowledge(activation: ReminderActivation) {
        mutableActivation.compareAndSet(activation, null)
    }

    fun retry() {
        refresh.tryEmit(Unit)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun runForAccount(account: String, database: AttendanceDatabase) {
        guarded(account) {
            activeAccount = account
            mutableSettings.value = AttendanceReminderSettings(account, preferences.enabledSince(account) != null)
            clearNotifications { it.account != account }
            mutableActivation.value?.takeIf { it.identity.account != account }?.let(::acknowledge)
        }
        val source = RoomClassAttendanceReminderSource(database)
        val changes = database.invalidationTracker.createFlow(
            "students",
            "student_packages",
            "attendance",
            "class_session_states",
            "class_schedules",
            "class_schedule_slots",
            "student_group_schedules",
            emitInitialState = false,
        ).map { Unit }
        val periodic = flow {
            while (true) {
                delay(15.minutes)
                emit(Unit)
            }
        }
        val onResume = refresh.onStart { emit(Unit) }.transformLatest {
            emit(Unit)
            delay(wakeDeliveryGrace)
            emit(Unit)
        }
        merge(changes, onResume, periodic).conflate().collect {
            guarded(account) {
                if (activeAccount == account) reconcile(account, source)
            }
        }
    }

    // The application deliberately keeps pending requests on a normal quit.
    suspend fun accountSignedOut(account: String) = guarded {
        if (activeAccount == account) {
            activeAccount = null
            mutableSettings.value = AttendanceReminderSettings()
            mutableActivation.value?.takeIf { it.identity.account == account }?.let(::acknowledge)
            clearNotifications { it.account == account }
        }
    }

    suspend fun setEnabled(account: String, enabled: Boolean) {
        guarded(account) {
            if (activeAccount != account) return@guarded
            if (!enabled) {
                preferences.setEnabledSince(account, null)
                mutableSettings.value = AttendanceReminderSettings(account)
                clearNotifications { it.account == account }
            } else {
                val permission = center.requestAuthorization()
                if (permission == NotificationAuthorization.ALLOWED && preferences.enabledSince(account) == null) {
                    preferences.setEnabledSince(account, Clock.System.now())
                }
                mutableSettings.value = AttendanceReminderSettings(
                    account = account,
                    enabled = permission == NotificationAuthorization.ALLOWED,
                    permissionDenied = permission == NotificationAuthorization.DENIED,
                )
            }
        }
        refresh.tryEmit(Unit)
    }

    private suspend fun reconcile(account: String, source: RoomClassAttendanceReminderSource) {
        val since = preferences.enabledSince(account)
        val authorization = center.authorization()
        mutableSettings.value = AttendanceReminderSettings(
            account = account,
            enabled = since != null,
            permissionDenied = authorization == NotificationAuthorization.DENIED,
        )
        trace("enabled=${since != null}, authorization=$authorization")
        if (since == null || authorization != NotificationAuthorization.ALLOWED) {
            clearNotifications { it.account == account }
            return
        }

        val now = Clock.System.now()
        val from = maxOf(minOf(since, now), now - 14.days)
        val candidates = source.getCandidates(from, now + 14.days)
        val title = getString(Res.string.attendance_reminder_title)
        val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
        val notifications = candidates.map { (session, name) ->
            val date = session.date.toJavaLocalDate().format(dateFormatter)
            ClassNotification(
                id = ClassNotificationIdentity(account, session.id).id,
                title = title,
                body = getString(Res.string.attendance_reminder_body, name, date),
                endsAt = session.endsAt,
            )
        }
        fun owned(id: String) = ClassNotificationIdentity.parse(id)?.account == account
        val pending = center.pending().filterKeys(::owned)
        val delivered = center.delivered().filterTo(mutableSetOf(), ::owned)
        val ids = notifications.mapTo(mutableSetOf()) { it.id }
        val acknowledged = preferences.acknowledged(account, ids, from)
        preferences.remember(account, delivered - acknowledged, now)
        val plan = planClassNotifications(notifications, pending, delivered, acknowledged, now)
        trace(
            "candidates=${notifications.size}, pending=${pending.size}, delivered=${delivered.size}, " +
                "new=${plan.schedule.size}, catchUp=${plan.catchUp.size}",
        )

        // Finish accepted OS writes before a signed-out account is cleared.
        withContext(NonCancellable) {
            center.remove(plan.removePending, plan.removeDelivered)
            plan.schedule.forEach { center.add(it, immediately = false) }
            plan.catchUp.forEach {
                center.add(it, immediately = true)
                preferences.remember(account, setOf(it.id), now)
            }
        }
        if (diagnostics) {
            val confirmed = center.pending()
            val upcoming = notifications.filter { it.endsAt > now }.sortedBy { it.endsAt }.take(5)
            val zone = ZoneId.systemDefault()
            for (notification in upcoming) {
                val time = java.time.Instant.ofEpochSecond(notification.endsAt.epochSeconds).atZone(zone)
                trace("endsAt=$time, queued=${confirmed[notification.id] == notification.revision}")
            }
            if (upcoming.isEmpty()) trace("no eligible upcoming classes")
        }
    }

    private fun trace(message: String) {
        if (diagnostics) System.err.println("[reminders] $message")
    }

    private suspend fun clearNotifications(filter: (ClassNotificationIdentity) -> Boolean) {
        fun shouldRemove(id: String) = ClassNotificationIdentity.parse(id)?.let(filter) == true
        center.remove(
            pending = center.pending().keys.filterTo(mutableSetOf(), ::shouldRemove),
            delivered = center.delivered().filterTo(mutableSetOf(), ::shouldRemove),
        )
    }

    private suspend fun guarded(account: String? = null, block: suspend () -> Unit) = mutex.withLock {
        try {
            withContext(Dispatchers.IO) { block() }
        } catch (error: TimeoutCancellationException) {
            reportFailure(account, error)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            reportFailure(account, error)
        }
    }

    private fun reportFailure(account: String?, error: Exception) {
        System.err.println("Simplio could not update attendance reminders: ${error.message}")
        if (account != null && activeAccount == account) {
            mutableSettings.value = mutableSettings.value.copy(failed = true)
        }
    }
}
