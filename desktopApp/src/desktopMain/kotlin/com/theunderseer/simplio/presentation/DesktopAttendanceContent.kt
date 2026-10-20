package com.theunderseer.simplio.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.theunderseer.simplio.database.AccountDatabaseSelection
import com.theunderseer.simplio.database.DesktopDatabaseManager
import com.theunderseer.simplio.di.DesktopAttendanceSession
import com.theunderseer.simplio.feature.attendance.data.sync.AttendanceUploadCoordinator
import com.theunderseer.simplio.feature.attendance.data.sync.AttendanceUploadStatus
import com.theunderseer.simplio.feature.attendance.data.sync.UploadProblem
import com.theunderseer.simplio.feature.attendance.presentation.AttendanceRoute
import com.theunderseer.simplio.shared.firebase.FirestoreRestClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.KoinIsolatedContext
import java.nio.file.Path

@Composable
internal fun DesktopAttendanceContent(
    userId: String?,
    databases: DesktopDatabaseManager,
    onCopyAttendance: (String) -> Unit,
    uploadClient: FirestoreRestClient? = null,
) {
    require(uploadClient == null || uploadClient.userId == userId)
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

        is DatabaseSelectionState.Ready -> AttendanceSessionContent(current.path, onCopyAttendance, uploadClient)

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
private fun AttendanceSessionContent(
    path: Path,
    onCopyAttendance: (String) -> Unit,
    uploadClient: FirestoreRestClient?,
) {
    var attempt by remember(path) { mutableIntStateOf(0) }
    var state by remember(path) { mutableStateOf<AttendanceSessionState>(AttendanceSessionState.Loading) }

    LaunchedEffect(path, uploadClient, attempt) {
        state = AttendanceSessionState.Loading
        val session = DesktopAttendanceSession(path.toString(), uploadClient)

        try {
            coroutineScope {
                session.initialize()
                session.uploader?.let { uploader -> launch { uploader.run() } }
                state = AttendanceSessionState.Ready(session)
                awaitCancellation()
            }
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
                Column(modifier = Modifier.fillMaxSize()) {
                    current.session.uploader?.let { UploadNotice(it) }
                    Box(modifier = Modifier.weight(1f)) {
                        AttendanceRoute(onCopyAttendance = onCopyAttendance)
                    }
                }
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

@Composable
private fun UploadNotice(uploader: AttendanceUploadCoordinator) {
    val status by uploader.status.collectAsState()
    val problem = (status as? AttendanceUploadStatus.Paused)?.problem
    val message = when {
        status is AttendanceUploadStatus.WaitingToRetry ->
            "Connection or server issue. Changes saved on this device. Retrying automatically."

        problem == UploadProblem.CONFLICT -> "Cloud data changed. Upload paused until changes are reviewed."

        problem == UploadProblem.AUTHENTICATION -> "Sign in again to upload changes. Local changes are saved."

        problem == UploadProblem.PERMISSION -> "Cloud access denied. Local changes are saved."

        problem == UploadProblem.TOO_LARGE -> "Some changes are too large to upload. Local changes are saved."

        problem == UploadProblem.TARGET_MISMATCH ->
            "This database belongs to another cloud account or project. Upload paused."

        problem != null -> "Upload paused. Local changes are saved."

        else -> null
    }
    if (message != null) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                message,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            if (problem in setOf(
                    UploadProblem.AUTHENTICATION,
                    UploadProblem.PERMISSION,
                    UploadProblem.SERVICE,
                    UploadProblem.INVALID_DATA,
                    UploadProblem.TOO_LARGE,
                )
            ) {
                TextButton(onClick = uploader::retry) { Text("Retry upload") }
            }
        }
    }
}
