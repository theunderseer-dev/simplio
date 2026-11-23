package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.room.Room
import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.database.configureAttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomAttendanceRoster
import com.theunderseer.simplio.feature.attendance.data.remote.createFirestoreAttendanceSync
import com.theunderseer.simplio.feature.attendance.data.sync.AttendanceSyncCoordinator
import com.theunderseer.simplio.feature.attendance.data.sync.AttendanceSyncStatus
import com.theunderseer.simplio.feature.attendance.di.attendanceModule
import com.theunderseer.simplio.feature.attendance.presentation.sync.AttendanceSyncNotice
import com.theunderseer.simplio.feature.attendance.resources.Res
import com.theunderseer.simplio.feature.attendance.resources.roster_error
import com.theunderseer.simplio.shared.firebase.FirestoreRestClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.time.Clock

@Suppress("FunctionNaming") // Compose UI functions use PascalCase.
@Composable
fun IosAttendanceHome(client: FirestoreRestClient, databasePath: () -> String?, onSignOut: () -> Unit) {
    val now by remember { attendanceClockTicks(Clock.System) }.collectAsState(initial = Clock.System.now())
    var attempt by remember(client) { mutableIntStateOf(0) }
    var state by remember(client) { mutableStateOf<AttendanceRosterState>(AttendanceRosterState.Loading) }
    var session by remember(client) { mutableStateOf<IosRosterSession?>(null) }
    var rosterShown by remember(client, attempt) { mutableStateOf(false) }

    LaunchedEffect(client, attempt) {
        state = AttendanceRosterState.Loading
        try {
            val path = withContext(Dispatchers.IO) { checkNotNull(databasePath()) }
            runRosterSession(client, path, onOpen = { session = it }, onState = { state = it })
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            session = null
            state = AttendanceRosterState.Error(Res.string.roster_error)
        }
    }

    val current = session
    val syncStatus = current?.sync?.status?.collectAsState()?.value
    val empty = (state as? AttendanceRosterState.Content)?.roster?.attendance?.entries?.isEmpty() == true
    val screenState = if (!rosterShown && empty && syncStatus == AttendanceSyncStatus.Syncing) {
        AttendanceRosterState.Loading
    } else {
        state
    }
    val showRoster = screenState is AttendanceRosterState.Content
    LaunchedEffect(client, attempt, showRoster) {
        if (showRoster) rosterShown = true
    }
    AttendanceRosterScreen(
        state = screenState,
        now = now,
        onRefresh = { if (current == null) attempt++ else current.sync.retry() },
        onSignOut = onSignOut,
        onEdit = { edit ->
            if (current == null) "Attendance isn't ready. Try again." else current.apply(edit)
        },
        creation = current?.creation,
        onDeleteStudent = current?.deleteStudent,
        syncNotice = { if (current != null) AttendanceSyncNotice(current.sync) },
    )
}

private class IosRosterSession(
    val sync: AttendanceSyncCoordinator,
    val apply: suspend (AttendanceRosterEdit) -> String?,
    val creation: IosAttendanceCreation,
    val deleteStudent: suspend (Int) -> String?,
)

private suspend fun runRosterSession(
    client: FirestoreRestClient,
    databasePath: String,
    onOpen: (IosRosterSession) -> Unit,
    onState: (AttendanceRosterState) -> Unit,
) {
    val database = Room.databaseBuilder<AttendanceDatabase>(name = databasePath)
        .configureAttendanceDatabase()
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
    try {
        val application = koinApplication {
            modules(module { single { database } }, attendanceModule())
        }
        try {
            coroutineScope {
                val dependencies = application.koin
                val sync = createFirestoreAttendanceSync(database, client)
                val roster = RoomAttendanceRoster(database)
                val edits = AttendanceRosterEdits(
                    dependencies.get(),
                    dependencies.get(),
                    dependencies.get(),
                    dependencies.get(),
                    dependencies.get(),
                )
                val creation = iosAttendanceCreation(dependencies.get(), dependencies.get(), dependencies.get())
                launch(start = CoroutineStart.UNDISPATCHED) { sync.run() }
                onOpen(
                    IosRosterSession(
                        sync = sync,
                        apply = { edit -> async { edits.apply(edit) }.await() },
                        creation = creation,
                        deleteStudent = { studentId -> async { edits.delete(studentId) }.await() },
                    ),
                )
                roster.observe().collect { onState(AttendanceRosterState.Content(it)) }
            }
        } finally {
            application.close()
        }
    } finally {
        withContext(NonCancellable + Dispatchers.IO) { database.close() }
    }
}
