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
import androidx.compose.runtime.collectAsState
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
import com.theunderseer.simplio.shared.firebase.auth.FirebaseSession
import com.theunderseer.simplio.shared.firebase.auth.FirebaseSessionManager
import com.theunderseer.simplio.shared.presentation.input.ValidatedOutlinedTextField
import com.theunderseer.simplio.shared.presentation.input.rememberValidatedInput
import com.theunderseer.simplio.shared.resources.Res
import com.theunderseer.simplio.shared.resources.sign_in_invalid_email
import com.theunderseer.simplio.shared.validation.InputPolicy
import com.theunderseer.simplio.shared.validation.isValidSignInEmail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun FirebaseSignInGate(
    sessionManager: FirebaseSessionManager,
    accessChecker: FirestoreAccessChecker,
    content: @Composable (FirebaseSession) -> Unit,
) {
    val session by sessionManager.session.collectAsState()
    var verifiedUserId by remember { mutableStateOf<String?>(null) }
    val emailInput = rememberValidatedInput("", InputPolicy.Email)
    val email = emailInput.text
    val invalidEmail = email.isNotEmpty() && !isValidSignInEmail(email)
    var password by remember { mutableStateOf("") }
    var isSigningIn by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val currentSession = session

    if (currentSession != null && currentSession.userId == verifiedUserId) {
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

                    ValidatedOutlinedTextField(
                        input = emailInput,
                        isError = invalidEmail,
                        supportingText = if (invalidEmail) {
                            { Text(stringResource(Res.string.sign_in_invalid_email)) }
                        } else {
                            null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSigningIn,
                        label = { Text("Email") },
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
                            isValidSignInEmail(email) &&
                            !emailInput.rejectedEdit &&
                            password.isNotEmpty(),
                        onClick = {
                            if (!isSigningIn) {
                                val submittedEmail = email.trim()
                                val submittedPassword = password

                                isSigningIn = true
                                errorMessage = null
                                verifiedUserId = null

                                coroutineScope.launch {
                                    var isCheckingAccess = false

                                    try {
                                        val signedInSession = sessionManager.signIn(
                                            email = submittedEmail,
                                            password = submittedPassword,
                                        )

                                        isCheckingAccess = true
                                        accessChecker.checkAccess()

                                        currentCoroutineContext().ensureActive()

                                        if (sessionManager.session.value !== signedInSession) {
                                            throw CancellationException("Authentication state changed")
                                        }

                                        password = ""
                                        verifiedUserId = signedInSession.userId
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
