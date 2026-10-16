package com.theunderseer.simplio.shared.firebase.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.theunderseer.simplio.shared.firebase.FirestoreAccessChecker
import com.theunderseer.simplio.shared.firebase.auth.FirebaseAuthClient
import com.theunderseer.simplio.shared.firebase.auth.FirebaseSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

@Composable
fun FirebaseSignInGate(
    authClient: FirebaseAuthClient,
    accessChecker: FirestoreAccessChecker,
    content: @Composable (FirebaseSession) -> Unit,
) {
    var session by remember { mutableStateOf<FirebaseSession?>(null) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSigningIn by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val currentSession = session

    if (currentSession != null) {
        content(currentSession)
        return
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 400.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = "Simplio",
                        style = MaterialTheme.typography.headlineLarge,
                    )

                    Text(
                        text = "Sign in",
                        style = MaterialTheme.typography.titleLarge,
                    )

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSigningIn,
                        label = { Text("Email") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                        ),
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSigningIn,
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                        ),
                    )

                    errorMessage?.let { message ->
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSigningIn &&
                            email.isNotBlank() &&
                            password.isNotEmpty(),
                        onClick = {
                            if (!isSigningIn) {
                                val submittedEmail = email.trim()
                                val submittedPassword = password

                                isSigningIn = true
                                errorMessage = null

                                coroutineScope.launch {
                                    var isCheckingAccess = false

                                    try {
                                        val signedInSession = authClient.signIn(
                                            email = submittedEmail,
                                            password = submittedPassword,
                                        )

                                        isCheckingAccess = true
                                        accessChecker.checkAccess(signedInSession)

                                        currentCoroutineContext().ensureActive()

                                        password = ""
                                        session = signedInSession
                                    } catch (error: CancellationException) {
                                        throw error
                                    } catch (_: Exception) {
                                        errorMessage = if (isCheckingAccess) {
                                            "Signed in, but cloud data is unavailable. " +
                                                "Please try again."
                                        } else {
                                            "Unable to sign in. Check your email, " +
                                                "password and connection."
                                        }
                                    } finally {
                                        isSigningIn = false
                                    }
                                }
                            }
                        },
                    ) {
                        Text(
                            text = if (isSigningIn) {
                                "Signing in…"
                            } else {
                                "Sign in"
                            },
                        )
                    }
                }
            }
        }
    }
}
