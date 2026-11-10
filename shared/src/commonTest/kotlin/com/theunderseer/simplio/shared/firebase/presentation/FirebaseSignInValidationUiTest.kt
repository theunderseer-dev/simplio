package com.theunderseer.simplio.shared.firebase.presentation

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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
import com.theunderseer.simplio.shared.firebase.createFirebaseHttpClient
import com.theunderseer.simplio.shared.fixtures.InputTestData
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals

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
            val http = createFirebaseHttpClient(
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
}
