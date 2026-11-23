package com.theunderseer.simplio.ios

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import com.theunderseer.simplio.feature.attendance.presentation.IosAttendanceHome
import com.theunderseer.simplio.shared.firebase.FirebaseConfig
import com.theunderseer.simplio.shared.firebase.FirestoreAccessChecker
import com.theunderseer.simplio.shared.firebase.FirestoreRestClient
import com.theunderseer.simplio.shared.firebase.auth.FirebaseAuthClient
import com.theunderseer.simplio.shared.firebase.auth.FirebaseSessionManager
import com.theunderseer.simplio.shared.firebase.auth.FirebaseSessionStorageException
import com.theunderseer.simplio.shared.firebase.createFirebaseHttpClient
import com.theunderseer.simplio.shared.firebase.presentation.FirebaseSignInGate
import com.theunderseer.simplio.shared.presentation.theme.SimplioTheme
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationProtectedDataDidBecomeAvailable
import platform.UIKit.UIViewController

fun makeAppController(apiKey: String, projectId: String, databasePath: (String, String) -> String?): UIViewController =
    ComposeUIViewController {
        SimplioTheme {
            Surface(Modifier.fillMaxSize()) {
                if (apiKey.isBlank() || projectId.isBlank()) {
                    Box(
                        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Set the Firebase project ID and API key in Firebase.local.xcconfig to sign in.")
                    }
                } else {
                    SignedInAttendance(FirebaseConfig(apiKey, projectId), databasePath)
                }
            }
        }
    }

@Suppress("FunctionNaming") // Compose UI functions use PascalCase.
@Composable
private fun SignedInAttendance(config: FirebaseConfig, databasePath: (String, String) -> String?) {
    val http = remember(config) { createFirebaseHttpClient() }
    DisposableEffect(http) { onDispose { http.close() } }
    val auth = remember(http, config) { FirebaseAuthClient(http, config) }
    val sessions = remember(auth, config.projectId) {
        FirebaseSessionManager(auth, sessionStore = IosFirebaseSessionStore(config.projectId))
    }
    val access = remember(http, config, sessions) { FirestoreAccessChecker(http, config, sessions) }
    val keychainAvailability = remember { keychainAvailabilityEvents() }

    FirebaseSignInGate(
        sessions,
        access,
        auth,
        keychainAvailability = keychainAvailability,
        formModifier = Modifier.safeDrawingPadding(),
    ) { session ->
        key(session.userId) {
            val scope = rememberCoroutineScope()
            var signingOut by remember { mutableStateOf(false) }
            var signOutFailed by remember { mutableStateOf(false) }
            val client = remember(session.userId, sessions) {
                FirestoreRestClient(http, config, sessions, userId = session.userId)
            }
            IosAttendanceHome(
                client,
                databasePath = { databasePath(config.projectId, session.userId) },
                onSignOut = {
                    if (!signingOut) {
                        signingOut = true
                        scope.launch {
                            try {
                                sessions.signOut()
                            } catch (_: FirebaseSessionStorageException) {
                                signOutFailed = true
                            } finally {
                                signingOut = false
                            }
                        }
                    }
                },
            )
            if (signOutFailed) {
                AlertDialog(
                    onDismissRequest = { signOutFailed = false },
                    title = { Text("Couldn't sign out") },
                    text = { Text("Couldn't clear the saved sign-in in Keychain. Unlock your device and try again.") },
                    confirmButton = { TextButton(onClick = { signOutFailed = false }) { Text("OK") } },
                )
            }
        }
    }
}

private fun keychainAvailabilityEvents(): Flow<Unit> = callbackFlow {
    val center = NSNotificationCenter.defaultCenter
    val observers = listOf(
        UIApplicationDidBecomeActiveNotification,
        UIApplicationProtectedDataDidBecomeAvailable,
    ).map { notification ->
        center.addObserverForName(notification, null, NSOperationQueue.mainQueue) { trySend(Unit) }
    }
    awaitClose { observers.forEach { center.removeObserver(it) } }
}.conflate()
