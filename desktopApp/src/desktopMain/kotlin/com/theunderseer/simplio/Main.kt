package com.theunderseer.simplio

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.theunderseer.simplio.database.DesktopDatabaseManager
import com.theunderseer.simplio.di.desktopFirebaseModule
import com.theunderseer.simplio.presentation.DesktopAttendanceContent
import com.theunderseer.simplio.shared.firebase.FirestoreAccessChecker
import com.theunderseer.simplio.shared.firebase.auth.FirebaseSessionManager
import com.theunderseer.simplio.shared.firebase.presentation.FirebaseSignInGate
import kotlinx.coroutines.launch
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import java.awt.datatransfer.StringSelection

fun main(appArgs: Array<String>) {
    val isLocalMode = "--local" in appArgs

    DesktopDatabaseManager.open().use { databases ->
        val firebase = if (isLocalMode) null else koinApplication { modules(desktopFirebaseModule()) }

        try {
            application(exitProcessOnExit = false) {
                Window(
                    onCloseRequest = ::exitApplication,
                    title = if (isLocalMode) "Simplio (local)" else "Simplio",
                ) {
                    DesktopApp(databases, firebase)
                }
            }
        } finally {
            firebase?.close()
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun DesktopApp(databases: DesktopDatabaseManager, firebase: KoinApplication?) {
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val onCopyAttendance: (String) -> Unit = { text ->
        coroutineScope.launch {
            clipboard.setClipEntry(ClipEntry(StringSelection(text)))
        }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (firebase == null) {
                DesktopAttendanceContent(
                    userId = null,
                    databases = databases,
                    onCopyAttendance = onCopyAttendance,
                )
            } else {
                FirebaseSignInGate(
                    sessionManager = firebase.koin.get<FirebaseSessionManager>(),
                    accessChecker = firebase.koin.get<FirestoreAccessChecker>(),
                ) { session ->
                    key(session.userId) {
                        DesktopAttendanceContent(
                            userId = session.userId,
                            databases = databases,
                            onCopyAttendance = onCopyAttendance,
                        )
                    }
                }
            }
        }
    }
}
