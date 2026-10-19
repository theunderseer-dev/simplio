package com.theunderseer.simplio.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.theunderseer.simplio.database.AccountDatabaseSelection
import com.theunderseer.simplio.database.DesktopDatabaseManager
import com.theunderseer.simplio.di.DesktopAttendanceSession
import com.theunderseer.simplio.feature.attendance.presentation.AttendanceRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import org.koin.compose.KoinIsolatedContext
import java.nio.file.Path

@Composable
internal fun DesktopAttendanceContent(
    userId: String?,
    databases: DesktopDatabaseManager,
    onCopyAttendance: (String) -> Unit,
) {
    var choice by remember(userId) { mutableStateOf<LegacyDataChoice?>(null) }
    var attempt by remember(userId) { mutableIntStateOf(0) }
    var state by remember(userId) { mutableStateOf<DatabaseSelectionState>(DatabaseSelectionState.Loading) }

    LaunchedEffect(databases, userId, choice, attempt) {
        state = DatabaseSelectionState.Loading

        try {
            state = withContext(Dispatchers.IO) {
                selectDatabase(databases, userId, choice)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            state = DatabaseSelectionState.Failed(error.message ?: "Unable to open local data")
        }
    }

    when (val current = state) {
        DatabaseSelectionState.Loading -> LoadingLocalData()
        DatabaseSelectionState.NeedsLegacyChoice -> ExistingDataDialog(
            onUseExisting = { choice = LegacyDataChoice.Existing },
            onStartEmpty = { choice = LegacyDataChoice.Empty },
        )

        is DatabaseSelectionState.Ready -> AttendanceSessionContent(current.path, onCopyAttendance)
        is DatabaseSelectionState.Failed -> LocalDataError(current.message, onRetry = { attempt++ })
    }
}

private fun selectDatabase(
    databases: DesktopDatabaseManager,
    userId: String?,
    choice: LegacyDataChoice?,
): DatabaseSelectionState {
    if (userId == null) {
        return DatabaseSelectionState.Ready(databases.localDatabase())
    }

    val selection = when (choice) {
        LegacyDataChoice.Existing -> AccountDatabaseSelection.Ready(databases.useExistingData(userId))
        LegacyDataChoice.Empty -> AccountDatabaseSelection.Ready(databases.useNewDatabase(userId))
        null -> databases.selectAccount(userId)
    }

    return when (selection) {
        is AccountDatabaseSelection.Ready -> DatabaseSelectionState.Ready(selection.path)
        AccountDatabaseSelection.LegacyDataAvailable -> DatabaseSelectionState.NeedsLegacyChoice
    }
}

@Composable
private fun AttendanceSessionContent(path: Path, onCopyAttendance: (String) -> Unit) {
    var attempt by remember(path) { mutableIntStateOf(0) }
    var state by remember(path) { mutableStateOf<AttendanceSessionState>(AttendanceSessionState.Loading) }

    LaunchedEffect(path, attempt) {
        state = AttendanceSessionState.Loading
        val session = DesktopAttendanceSession(path.toString())

        try {
            session.initialize()
            state = AttendanceSessionState.Ready(session)
            awaitCancellation()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            state = AttendanceSessionState.Failed(error.message ?: "Unable to load local data")
        } finally {
            session.close()
        }
    }

    when (val current = state) {
        AttendanceSessionState.Loading -> LoadingLocalData()
        is AttendanceSessionState.Failed -> LocalDataError(current.message, onRetry = { attempt++ })
        is AttendanceSessionState.Ready -> KoinIsolatedContext(context = current.session.application) {
            CompositionLocalProvider(LocalViewModelStoreOwner provides current.session) {
                AttendanceRoute(onCopyAttendance = onCopyAttendance)
            }
        }
    }
}

@Composable
private fun ExistingDataDialog(onUseExisting: () -> Unit, onStartEmpty: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Use existing data?") },
        text = {
            Text(
                "Students, packages and class history are already stored on this computer. " +
                    "Use them for the account you just signed in to, or start with an empty list.",
            )
        },
        confirmButton = {
            TextButton(onClick = onUseExisting) { Text("Use existing data") }
        },
        dismissButton = {
            TextButton(onClick = onStartEmpty) { Text("Start empty") }
        },
    )
}

@Composable
private fun LoadingLocalData() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun LocalDataError(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(message, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

private enum class LegacyDataChoice {
    Existing,
    Empty,
}

private sealed interface DatabaseSelectionState {
    data object Loading : DatabaseSelectionState

    data object NeedsLegacyChoice : DatabaseSelectionState

    data class Ready(val path: Path) : DatabaseSelectionState

    data class Failed(val message: String) : DatabaseSelectionState
}

private sealed interface AttendanceSessionState {
    data object Loading : AttendanceSessionState

    data class Ready(val session: DesktopAttendanceSession) : AttendanceSessionState

    data class Failed(val message: String) : AttendanceSessionState
}
