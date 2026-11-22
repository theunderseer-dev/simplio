package com.theunderseer.simplio.shared.firebase.presentation

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.shared.firebase.FirebaseConfig
import com.theunderseer.simplio.shared.firebase.FirestoreAccessChecker
import com.theunderseer.simplio.shared.firebase.auth.FirebaseAuthApi
import com.theunderseer.simplio.shared.firebase.auth.FirebaseAuthClient
import com.theunderseer.simplio.shared.firebase.auth.FirebaseSession
import com.theunderseer.simplio.shared.firebase.auth.FirebaseSessionManager
import com.theunderseer.simplio.shared.firebase.auth.FirebaseSessionStorageException
import com.theunderseer.simplio.shared.firebase.auth.FirebaseSessionStorageOperation
import com.theunderseer.simplio.shared.firebase.auth.FirebaseSessionStore
import com.theunderseer.simplio.shared.firebase.auth.RememberedFirebaseSession
import com.theunderseer.simplio.shared.firebase.createFirebaseHttpClient
import com.theunderseer.simplio.shared.fixtures.InputTestData
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class FirebaseSignInValidationUiTest {
    @Test
    fun `invalid email blocks sign in while valid email and an unchanged password reach the provider`() =
        runComposeUiTest {
            val submissions = mutableListOf<Pair<String, String>>()
            val sessions = FirebaseSessionManager(object : FirebaseAuthApi {
                override suspend fun signIn(email: String, password: String): FirebaseSession {
                    submissions += email to password
                    return FirebaseSession("test-user", "id-token", "refresh-token", 3600)
                }
                override suspend fun refreshSession(refreshToken: String): FirebaseSession = error("Not expected")
            })
            val config = FirebaseConfig("test-key", "test-project")
            val http = authHttpClient()
            try {
                val access = FirestoreAccessChecker(http, config, sessions)
                val accounts = FirebaseAuthClient(http, config)
                setContent { FirebaseSignInGate(sessions, access, accounts) { Text("Signed-in content") } }
                onNodeWithText("Password").performTextInput(InputTestData.passwordWithSpaces)
                onNodeWithText("Email").performTextInput("teacher")
                onNodeWithText("Enter an email address, e.g. name@example.com.").assertIsDisplayed()
                onNode(hasText("Sign in") and hasClickAction()).assertIsNotEnabled()
                assertEquals(emptyList(), submissions)
                onNodeWithText("Email").performTextReplacement(InputTestData.email)
                onNode(hasText("Sign in") and hasClickAction()).assertIsEnabled().performClick()
                waitUntil(timeoutMillis = 5000) {
                    runCatching { onNodeWithText("Signed-in content").assertIsDisplayed() }.isSuccess
                }
                onNodeWithText("Signed-in content").assertIsDisplayed()
                assertEquals(listOf(InputTestData.email to InputTestData.passwordWithSpaces), submissions)
            } finally {
                http.close()
            }
        }

    @Test
    fun `remember me is opt-in and its label toggles the checkbox after retrying Keychain`() = runComposeUiTest {
        var saved: RememberedFirebaseSession? = null
        val readInteractions = mutableListOf<Boolean>()
        val store = object : FirebaseSessionStore {
            override suspend fun read(allowUserInteraction: Boolean): RememberedFirebaseSession? {
                readInteractions += allowUserInteraction
                if (!allowUserInteraction) throw FirebaseSessionStorageException()
                return saved
            }

            override suspend fun write(session: RememberedFirebaseSession, allowUserInteraction: Boolean) {
                saved = session
            }

            override suspend fun clear(allowUserInteraction: Boolean) {
                saved = null
            }
        }
        val sessions = FirebaseSessionManager(
            authApi = object : FirebaseAuthApi {
                override suspend fun signIn(email: String, password: String) =
                    FirebaseSession("test-user", "id-token", "refresh-token", 3600)

                override suspend fun refreshSession(refreshToken: String): FirebaseSession = error("Not expected")
            },
            sessionStore = store,
        )
        val config = FirebaseConfig("test-key", "test-project")
        val http = authHttpClient()
        try {
            val access = FirestoreAccessChecker(http, config, sessions)
            val accounts = FirebaseAuthClient(http, config)
            setContent { FirebaseSignInGate(sessions, access, accounts) { Text("Signed-in content") } }

            onNodeWithText("Retry saved sign-in").assertIsDisplayed().performClick()
            onNodeWithText("Retry saved sign-in").assertDoesNotExist()
            assertEquals(listOf(false, true), readInteractions)
            assertNull(saved)

            val remember = onNodeWithText("Remember me")
            remember.assertIsOff().performClick()
            remember.assertIsOn()
            onNodeWithText("Email").performTextInput(InputTestData.email)
            onNodeWithText("Password").performTextInput(InputTestData.passwordWithSpaces)
            onNode(hasText("Sign in") and hasClickAction()).performClick()
            waitUntil(timeoutMillis = 5000) {
                runCatching { onNodeWithText("Signed-in content").assertIsDisplayed() }.isSuccess
            }
            assertEquals("test-user", saved?.userId)
            assertEquals("refresh-token", saved?.refreshToken)
        } finally {
            http.close()
        }
    }

    @Test
    fun `temporary Keychain read retries silently when the app reports availability`() = runComposeUiTest {
        val availability = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val readInteractions = mutableListOf<Boolean>()
        val store = object : FirebaseSessionStore {
            override suspend fun read(allowUserInteraction: Boolean): RememberedFirebaseSession {
                readInteractions += allowUserInteraction
                if (readInteractions.size == 1) {
                    throw FirebaseSessionStorageException(
                        operation = FirebaseSessionStorageOperation.Read,
                        status = -25_308,
                        isTemporarilyUnavailable = true,
                    )
                }
                return RememberedFirebaseSession("test-user", "refresh-token")
            }

            override suspend fun write(session: RememberedFirebaseSession, allowUserInteraction: Boolean) {
                error("An unchanged token must not be written")
            }

            override suspend fun clear(allowUserInteraction: Boolean) = Unit
        }
        val sessions = FirebaseSessionManager(
            authApi = object : FirebaseAuthApi {
                override suspend fun signIn(email: String, password: String): FirebaseSession = error("Not expected")

                override suspend fun refreshSession(refreshToken: String) =
                    FirebaseSession("test-user", "refreshed-id-token", refreshToken, 3600)
            },
            sessionStore = store,
        )
        val config = FirebaseConfig("test-key", "test-project")
        val http = authHttpClient()
        try {
            val access = FirestoreAccessChecker(http, config, sessions)
            val accounts = FirebaseAuthClient(http, config)
            setContent {
                FirebaseSignInGate(sessions, access, accounts, keychainAvailability = availability) {
                    Text("Signed-in content")
                }
            }
            onNodeWithText("(read, OSStatus -25308)", substring = true).assertIsDisplayed()
            waitUntil(timeoutMillis = 5000) { availability.subscriptionCount.value == 1 }
            runOnIdle { assertTrue(availability.tryEmit(Unit)) }
            waitUntil(timeoutMillis = 5000) {
                runCatching { onNodeWithText("Signed-in content").assertIsDisplayed() }.isSuccess
            }
            assertEquals(listOf(false, false), readInteractions)
        } finally {
            http.close()
        }
    }

    @Test
    fun `availability after password sign-in keeps the signed-in screen open`() = runComposeUiTest {
        val availability = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        var reads = 0
        val store = object : FirebaseSessionStore {
            override suspend fun read(allowUserInteraction: Boolean): RememberedFirebaseSession? {
                reads++
                throw FirebaseSessionStorageException(
                    operation = FirebaseSessionStorageOperation.Read,
                    isTemporarilyUnavailable = true,
                )
            }

            override suspend fun write(session: RememberedFirebaseSession, allowUserInteraction: Boolean) =
                error("Not expected")

            override suspend fun clear(allowUserInteraction: Boolean) = Unit
        }
        val sessions = FirebaseSessionManager(
            authApi = object : FirebaseAuthApi {
                override suspend fun signIn(email: String, password: String) =
                    FirebaseSession("test-user", "id-token", "refresh-token", 3600)

                override suspend fun refreshSession(refreshToken: String): FirebaseSession = error("Not expected")
            },
            sessionStore = store,
        )
        val config = FirebaseConfig("test-key", "test-project")
        val http = authHttpClient()
        try {
            val access = FirestoreAccessChecker(http, config, sessions)
            val accounts = FirebaseAuthClient(http, config)
            setContent {
                FirebaseSignInGate(sessions, access, accounts, keychainAvailability = availability) {
                    Text("Signed-in content")
                }
            }
            onNodeWithText("Retry saved sign-in").assertIsDisplayed()
            onNodeWithText("Email").performTextInput(InputTestData.email)
            onNodeWithText("Password").performTextInput(InputTestData.passwordWithSpaces)
            onNode(hasText("Sign in") and hasClickAction()).performClick()
            waitUntil(timeoutMillis = 5000) {
                runCatching { onNodeWithText("Signed-in content").assertIsDisplayed() }.isSuccess
            }
            assertEquals(1, availability.subscriptionCount.value)
            runOnIdle { assertTrue(availability.tryEmit(Unit)) }
            onNodeWithText("Signed-in content").assertIsDisplayed()
            assertEquals(1, reads)
        } finally {
            http.close()
        }
    }

    private fun authHttpClient() = createFirebaseHttpClient(
        MockEngine { request ->
            if (request.url.encodedPath == "/v1/accounts:lookup") {
                respond(
                    """{"users":[{"localId":"test-user","emailVerified":true}]}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            } else {
                respond("{}")
            }
        },
    )
}
