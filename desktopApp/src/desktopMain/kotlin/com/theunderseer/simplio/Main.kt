package com.theunderseer.simplio

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.theunderseer.simplio.database.DesktopDatabaseManager
import com.theunderseer.simplio.di.desktopFirebaseModule
import com.theunderseer.simplio.notifications.DesktopAttendanceReminders
import com.theunderseer.simplio.notifications.MacClassNotificationCenter
import com.theunderseer.simplio.notifications.reminderAccount
import com.theunderseer.simplio.presentation.DesktopAttendanceContent
import com.theunderseer.simplio.shared.firebase.FirestoreAccessChecker
import com.theunderseer.simplio.shared.firebase.FirestoreRestClient
import com.theunderseer.simplio.shared.firebase.auth.FirebaseSessionManager
import com.theunderseer.simplio.shared.firebase.presentation.FirebaseSignInGate
import com.theunderseer.simplio.shared.presentation.theme.SimplioTheme
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.KoinApplication
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import java.awt.GraphicsEnvironment
import java.awt.datatransfer.StringSelection

fun main(appArgs: Array<String>) {
    val isLocalMode = "--local" in appArgs
    val reminderDiagnostics = "--reminder-diagnostics" in appArgs
    val notificationCenter = MacClassNotificationCenter.open(reminderDiagnostics)
    if (reminderDiagnostics) {
        System.err.println("[reminders] native center ready=${notificationCenter != null}")
    }
    val reminders = notificationCenter?.let { DesktopAttendanceReminders(it, reminderDiagnostics) }

    DesktopDatabaseManager.open().use { databases ->
        val firebase = if (isLocalMode) null else koinApplication { modules(desktopFirebaseModule()) }

        try {
            application(exitProcessOnExit = false) {
                val initialSize = remember {
                    val availableBounds = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
                    DpSize(
                        width = minOf(800, availableBounds.width).dp,
                        height = minOf(840, availableBounds.height).dp,
                    )
                }
                val windowState = rememberWindowState(
                    placement = WindowPlacement.Maximized,
                    position = WindowPosition(Alignment.Center),
                    size = initialSize,
                )

                Window(
                    state = windowState,
                    onCloseRequest = ::exitApplication,
                    title = if (isLocalMode) "Simplio (local)" else "Simplio",
                ) {
                    if (reminders != null) {
                        LaunchedEffect(reminders) {
                            reminders.observeNativeEvents {
                                windowState.isMinimized = false
                                window.toFront()
                            }
                        }
                    }
                    DesktopApp(databases, firebase, reminders)
                }
            }
        } finally {
            firebase?.close()
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun DesktopApp(
    databases: DesktopDatabaseManager,
    firebase: KoinApplication?,
    reminders: DesktopAttendanceReminders?,
) {
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val onCopyAttendance: (String) -> Unit = { text ->
        coroutineScope.launch {
            clipboard.setClipEntry(ClipEntry(StringSelection(text)))
        }
    }

    SimplioTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (firebase == null) {
                DesktopAttendanceContent(
                    userId = null,
                    databases = databases,
                    onCopyAttendance = onCopyAttendance,
                    reminders = reminders,
                )
            } else {
                val sessions = remember(firebase) { firebase.koin.get<FirebaseSessionManager>() }
                if (reminders != null) {
                    LaunchedEffect(sessions, reminders) {
                        var previous: String? = null
                        sessions.session.map { it?.userId }.distinctUntilChanged().collect { current ->
                            previous?.takeIf { it != current }?.let {
                                reminders.accountSignedOut(reminderAccount(it))
                            }
                            previous = current
                        }
                    }
                }
                FirebaseSignInGate(
                    sessionManager = sessions,
                    accessChecker = firebase.koin.get<FirestoreAccessChecker>(),
                ) { session ->
                    key(session.userId) {
                        DesktopAttendanceContent(
                            userId = session.userId,
                            uploadClient = remember(firebase, session.userId) {
                                firebase.koin.get<FirestoreRestClient> { parametersOf(session.userId) }
                            },
                            databases = databases,
                            onCopyAttendance = onCopyAttendance,
                            reminders = reminders,
                        )
                    }
                }
            }
        }
    }
}
