package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.sync.CloudAttendanceRoster
import com.theunderseer.simplio.feature.attendance.data.remote.FirestoreAttendancePullRemote
import com.theunderseer.simplio.feature.attendance.data.sync.UploadTarget
import com.theunderseer.simplio.feature.attendance.resources.Res
import com.theunderseer.simplio.feature.attendance.resources.roster_error
import com.theunderseer.simplio.feature.attendance.resources.roster_permission_denied
import com.theunderseer.simplio.feature.attendance.resources.roster_sign_in_again
import com.theunderseer.simplio.shared.firebase.FirestoreFailure
import com.theunderseer.simplio.shared.firebase.FirestoreFailureKind
import com.theunderseer.simplio.shared.firebase.FirestoreRestClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlin.time.Clock

@Suppress("FunctionNaming") // Compose UI functions use PascalCase.
@Composable
fun IosAttendanceHome(client: FirestoreRestClient, onSignOut: () -> Unit) {
    val now by remember { attendanceClockTicks(Clock.System) }.collectAsState(initial = Clock.System.now())
    val refreshes = remember(client) { Channel<Unit>(Channel.CONFLATED) }
    var state by remember(client) { mutableStateOf<AttendanceRosterState>(AttendanceRosterState.Loading) }

    LaunchedEffect(client) {
        val database = Room.inMemoryDatabaseBuilder<AttendanceDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        try {
            val roster = CloudAttendanceRoster(
                database = database,
                target = UploadTarget(client.projectId, client.userId),
                remote = FirestoreAttendancePullRemote(client),
            )
            refreshes.trySend(Unit)
            for (request in refreshes) {
                state = AttendanceRosterState.Loading
                state = loadRoster(roster)
            }
        } finally {
            refreshes.close()
            withContext(NonCancellable + Dispatchers.IO) { database.close() }
        }
    }

    AttendanceRosterScreen(state, now, onRefresh = { refreshes.trySend(Unit) }, onSignOut = onSignOut)
}

private suspend fun loadRoster(roster: CloudAttendanceRoster): AttendanceRosterState = try {
    AttendanceRosterState.Content(roster.getAll())
} catch (error: CancellationException) {
    throw error
} catch (error: FirestoreFailure) {
    AttendanceRosterState.Error(
        when (error.kind) {
            FirestoreFailureKind.AUTHENTICATION -> Res.string.roster_sign_in_again
            FirestoreFailureKind.PERMISSION -> Res.string.roster_permission_denied
            else -> Res.string.roster_error
        },
    )
} catch (_: Exception) {
    AttendanceRosterState.Error(Res.string.roster_error)
}
