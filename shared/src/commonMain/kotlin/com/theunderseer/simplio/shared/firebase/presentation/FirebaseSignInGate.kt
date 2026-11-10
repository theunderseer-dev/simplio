package com.theunderseer.simplio.shared.firebase.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.theunderseer.simplio.shared.firebase.FirestoreAccessChecker
import com.theunderseer.simplio.shared.firebase.FirestoreAccessException
import com.theunderseer.simplio.shared.firebase.auth.FirebaseAuthClient
import com.theunderseer.simplio.shared.firebase.auth.FirebaseAuthException
import com.theunderseer.simplio.shared.firebase.auth.FirebaseSession
import com.theunderseer.simplio.shared.firebase.auth.FirebaseSessionManager
import com.theunderseer.simplio.shared.firebase.auth.FirebaseSessionStorageException
import com.theunderseer.simplio.shared.presentation.components.SimplioButton
import com.theunderseer.simplio.shared.presentation.input.ValidatedOutlinedTextField
import com.theunderseer.simplio.shared.presentation.input.rememberValidatedInput
import com.theunderseer.simplio.shared.presentation.theme.positive
import com.theunderseer.simplio.shared.resources.Res
import com.theunderseer.simplio.shared.resources.sign_in_invalid_email
import com.theunderseer.simplio.shared.validation.InputPolicy
import com.theunderseer.simplio.shared.validation.isValidSignInEmail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun FirebaseSignInGate(
    sessionManager: FirebaseSessionManager,
    accessChecker: FirestoreAccessChecker,
    accountClient: FirebaseAuthClient,
    content: @Composable (FirebaseSession) -> Unit,
) {
    val session by sessionManager.session.collectAsState()
    var verifiedUserId by remember { mutableStateOf<String?>(null) }
    var form by remember { mutableStateOf(AuthForm.SignIn) }
    var verificationSession by remember { mutableStateOf<FirebaseSession?>(null) }
    val emailInput = rememberValidatedInput("", InputPolicy.Email)
    val email = emailInput.text
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(false) }
    var restoring by remember(sessionManager) { mutableStateOf(sessionManager.canRememberSession) }
    var restoreFailed by remember { mutableStateOf(false) }
    var restoreAttempt by remember { mutableIntStateOf(0) }
    var info by remember { mutableStateOf<AuthNotice?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(sessionManager, restoreAttempt) {
        if (!sessionManager.canRememberSession) return@LaunchedEffect
        restoring = true
        restoreFailed = false
        errorMessage = null
        try {
            val restored = sessionManager.restoreSession { candidate ->
                if (!accountClient.isEmailVerified(candidate)) throw VerificationRequired(candidate)
                accessChecker.checkAccess(candidate)
            }
            verifiedUserId = restored?.userId
        } catch (error: CancellationException) {
            throw error
        } catch (_: VerificationRequired) {
            errorMessage = "Please sign in again to confirm your email."
        } catch (_: Exception) {
            restoreFailed = true
            errorMessage = "Couldn't restore your sign-in. Try again or sign in with your password."
        } finally {
            restoring = false
        }
    }

    if (restoring) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(contentAlignment = Alignment.Center) { Text("Restoring sign-in…") }
        }
        return
    }

    val currentSession = session
    if (currentSession != null && currentSession.userId == verifiedUserId) {
        content(currentSession)
        return
    }

    fun showForm(next: AuthForm) {
        form = next
        password = ""
        info = null
        errorMessage = null
        if (next != AuthForm.Verify) verificationSession = null
    }

    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val verificationCandidate = verificationSession.takeIf { form == AuthForm.Verify && !busy && windowFocused }
    LaunchedEffect(verificationCandidate) {
        val candidate = verificationCandidate ?: return@LaunchedEffect
        // Check again when the user returns from the browser, with short retries while the window is focused.
        repeat(12) { attempt ->
            if (attempt > 0) delay(5_000)
            val verified = try {
                accountClient.isEmailVerified(candidate)
            } catch (error: CancellationException) {
                throw error
            } catch (error: FirebaseAuthException) {
                if (error.requiresSignIn || error.statusCode == 429) return@LaunchedEffect
                false
            } catch (_: Exception) {
                false
            }
            if (verified && verificationSession === candidate && form == AuthForm.Verify && !busy) {
                showForm(AuthForm.SignIn)
                info = AuthNotice.EmailVerified
                return@LaunchedEffect
            }
        }
    }

    fun submit() {
        if (busy) return
        val submittedForm = form
        val submittedEmail = email.trim()
        val submittedPassword = password
        val submittedRemember = rememberMe
        busy = true
        errorMessage = null
        info = null
        scope.launch {
            var checkingCloud = false
            var checkingVerification = false
            try {
                when (submittedForm) {
                    AuthForm.SignIn -> {
                        verifiedUserId = null
                        val signedInSession = sessionManager.signIn(
                            submittedEmail,
                            submittedPassword,
                            rememberSession = submittedRemember,
                        ) { candidate ->
                            checkingVerification = true
                            if (!accountClient.isEmailVerified(candidate)) throw VerificationRequired(candidate)
                            checkingVerification = false
                            checkingCloud = true
                            accessChecker.checkAccess(candidate)
                            checkingCloud = false
                        }
                        password = ""
                        rememberMe = false
                        restoreFailed = false
                        verifiedUserId = signedInSession.userId
                    }

                    AuthForm.Register -> {
                        val account = accountClient.createAccount(submittedEmail, submittedPassword)
                        verificationSession = account
                        showForm(AuthForm.Verify)
                        accountClient.sendVerificationEmail(account)
                        info = AuthNotice.VerificationSent
                    }

                    AuthForm.Reset -> {
                        accountClient.sendPasswordResetEmail(submittedEmail)
                        showForm(AuthForm.SignIn)
                        info = AuthNotice.ResetRequested
                    }

                    AuthForm.Verify -> {
                        accountClient.sendVerificationEmail(checkNotNull(verificationSession))
                        info = AuthNotice.VerificationSent
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (required: VerificationRequired) {
                verificationSession = required.session
                showForm(AuthForm.Verify)
            } catch (error: Exception) {
                errorMessage = authError(form, checkingCloud, checkingVerification, error)
            } finally {
                busy = false
            }
        }
    }

    val validEmail = isValidSignInEmail(email) && !emailInput.rejectedEdit
    val invalidEmail = email.isNotEmpty() && !isValidSignInEmail(email)
    val canSubmit = when (form) {
        AuthForm.SignIn -> validEmail && password.isNotEmpty()
        AuthForm.Register -> validEmail && password.length >= 6
        AuthForm.Reset -> validEmail
        AuthForm.Verify -> verificationSession != null
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Simplio", style = MaterialTheme.typography.headlineLarge)
                Text(form.title, style = MaterialTheme.typography.titleLarge)

                if (form == AuthForm.Verify) {
                    Text("Open the verification link for ${email.trim()}, then return to Simplio.")
                } else {
                    ValidatedOutlinedTextField(
                        input = emailInput,
                        isError = invalidEmail,
                        supportingText = if (invalidEmail) {
                            { Text(stringResource(Res.string.sign_in_invalid_email)) }
                        } else {
                            null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy,
                        label = { Text("Email") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    )
                }

                if (form == AuthForm.SignIn || form == AuthForm.Register) {
                    OutlinedTextField(
                        shape = MaterialTheme.shapes.small,
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy,
                        label = { Text("Password") },
                        supportingText = if (form == AuthForm.Register) {
                            { Text("At least 6 characters") }
                        } else {
                            null
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                }

                if (form == AuthForm.SignIn && sessionManager.canRememberSession) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = rememberMe, onCheckedChange = { rememberMe = it }, enabled = !busy)
                        Text("Remember me on this Mac")
                    }
                }

                info?.let { notice ->
                    if (notice == AuthNotice.EmailVerified) {
                        Text(notice.text, color = MaterialTheme.colorScheme.positive)
                    } else {
                        Text(notice.text)
                    }
                }
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                SimplioButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy && canSubmit,
                    onClick = ::submit,
                ) {
                    Text(if (busy) "Please wait…" else form.action)
                }

                if (form == AuthForm.SignIn) {
                    Column {
                        if (restoreFailed) {
                            TextButton(onClick = {
                                restoring = true
                                restoreAttempt++
                            }, enabled = !busy) {
                                Text("Retry saved sign-in")
                            }
                        }
                        TextButton(onClick = { showForm(AuthForm.Register) }, enabled = !busy) {
                            Text("Create account")
                        }
                        TextButton(onClick = { showForm(AuthForm.Reset) }, enabled = !busy) {
                            Text("Forgot password?")
                        }
                    }
                } else {
                    TextButton(onClick = { showForm(AuthForm.SignIn) }, enabled = !busy) {
                        Text("Back to sign in")
                    }
                }
            }
        }
    }
}

private enum class AuthNotice(val text: String) {
    EmailVerified("Email verified. Sign in to continue."),
    VerificationSent("Verification email sent."),
    ResetRequested("If an account exists for this email, you'll receive a password reset link."),
}

private enum class AuthForm(val title: String, val action: String) {
    SignIn("Sign in", "Sign in"),
    Register("Create account", "Create account"),
    Reset("Reset password", "Send reset email"),
    Verify("Verify your email", "Resend verification email"),
}

private class VerificationRequired(val session: FirebaseSession) : IllegalStateException()

private fun authError(
    form: AuthForm,
    checkingCloud: Boolean,
    checkingVerification: Boolean,
    error: Exception,
): String {
    if (error is FirebaseSessionStorageException) {
        return "Couldn't update the saved sign-in. Check access to macOS Keychain and try again."
    }
    if (checkingCloud) {
        return if ((error as? FirestoreAccessException)?.statusCode == 403) {
            "Signed in, but this account doesn't have access to cloud data."
        } else {
            "Signed in, but cloud data is unavailable. Please try again."
        }
    }

    val firebaseError = error as? FirebaseAuthException
    if (firebaseError?.statusCode == 429 || firebaseError?.errorCode == "TOO_MANY_ATTEMPTS_TRY_LATER") {
        return "Too many requests. Please try again later."
    }
    if (checkingVerification) return "Unable to check email verification. Please try signing in again."
    return when (form) {
        AuthForm.SignIn -> "Unable to sign in. Check your email, password and connection."

        AuthForm.Register -> when (firebaseError?.errorCode) {
            "EMAIL_EXISTS" -> "Try signing in or resetting your password for this email."
            "WEAK_PASSWORD", "PASSWORD_DOES_NOT_MEET_REQUIREMENTS" -> "Choose a stronger password."
            else -> "Unable to create an account. Check your connection or try signing in."
        }

        AuthForm.Reset -> "Unable to request a reset email. Please try again."

        AuthForm.Verify -> "Unable to send the verification email. Try again or return to sign in."
    }
}
