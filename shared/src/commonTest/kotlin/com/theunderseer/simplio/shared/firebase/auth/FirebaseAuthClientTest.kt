package com.theunderseer.simplio.shared.firebase.auth

import com.theunderseer.simplio.shared.firebase.FirebaseConfig
import com.theunderseer.simplio.shared.firebase.createFirebaseHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.parseQueryString
import io.ktor.serialization.JsonConvertException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FirebaseAuthClientTest {
    private val config = FirebaseConfig(
        apiKey = "test-api-key",
        projectId = "test-project",
    )

    private val email = "owner@example.com"
    private val password = "test-password"

    @Test
    fun `signs in with credentials and restores session from response`() = runTest {
        // Given
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("https", request.url.protocol.name)
            assertEquals(
                "identitytoolkit.googleapis.com",
                request.url.host,
            )
            assertEquals(
                "/v1/accounts:signInWithPassword",
                request.url.encodedPath,
            )
            assertEquals(
                config.apiKey,
                request.url.parameters["key"],
            )
            assertEquals(
                ContentType.Application.Json,
                request.body.contentType?.withoutParameters(),
            )

            val body = Json.parseToJsonElement(
                request.body.toByteArray().decodeToString(),
            ).jsonObject

            assertEquals(JsonPrimitive(email), body["email"])
            assertEquals(JsonPrimitive(password), body["password"])
            assertEquals(
                JsonPrimitive(true),
                body["returnSecureToken"],
            )

            respond(
                content = """
                    {
                      "localId": "user-123",
                      "idToken": "test-id-token",
                      "refreshToken": "test-refresh-token",
                      "expiresIn": "3600",
                      "registered": true
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType,
                    ContentType.Application.Json.toString(),
                ),
            )
        }

        withAuthClient(engine) { client ->
            // When
            val session = client.signIn(
                email = email,
                password = password,
            )

            // Then
            assertEquals("user-123", session.userId)
            assertEquals("test-id-token", session.idToken)
            assertEquals("test-refresh-token", session.refreshToken)
            assertEquals(3_600L, session.expiresInSeconds)
        }
    }

    @Test
    fun `rejects unsuccessful responses instead of creating session`() = runTest {
        val statuses = listOf(
            HttpStatusCode.BadRequest,
            HttpStatusCode.TooManyRequests,
            HttpStatusCode.ServiceUnavailable,
        )

        statuses.forEach { status ->
            // Given
            val engine = MockEngine {
                respond(
                    content = "{}",
                    status = status,
                    headers = headersOf(
                        HttpHeaders.ContentType,
                        ContentType.Application.Json.toString(),
                    ),
                )
            }

            withAuthClient(engine) { client ->
                // When / Then
                assertFailsWith<IllegalStateException>(
                    message = "Expected sign-in to fail for $status",
                ) {
                    client.signIn(
                        email = email,
                        password = password,
                    )
                }
            }
        }
    }

    @Test
    fun `rejects successful response without required session fields`() = runTest {
        // Given
        val engine = MockEngine {
            respond(
                content = """{"localId":"user-123"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType,
                    ContentType.Application.Json.toString(),
                ),
            )
        }

        withAuthClient(engine) { client ->
            // When / Then
            assertFailsWith<JsonConvertException> {
                client.signIn(
                    email = email,
                    password = password,
                )
            }
        }
    }

    @Test
    fun `refreshes session and uses rotated refresh token`() = runTest {
        // Given: the token contains characters that require form encoding
        val refreshToken = "refresh+token/with=symbols"

        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("https", request.url.protocol.name)
            assertEquals(
                "securetoken.googleapis.com",
                request.url.host,
            )
            assertEquals("/v1/token", request.url.encodedPath)
            assertEquals(
                config.apiKey,
                request.url.parameters["key"],
            )
            assertEquals(
                ContentType.Application.FormUrlEncoded,
                request.body.contentType?.withoutParameters(),
            )

            val form = parseQueryString(
                request.body.toByteArray().decodeToString(),
            )

            assertEquals("refresh_token", form["grant_type"])
            assertEquals(refreshToken, form["refresh_token"])

            respond(
                content = """
                {
                  "user_id": "user-123",
                  "id_token": "new-id-token",
                  "refresh_token": "new-refresh-token",
                  "expires_in": "3600",
                  "token_type": "Bearer"
                }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType,
                    ContentType.Application.Json.toString(),
                ),
            )
        }

        withAuthClient(engine) { client ->
            // When
            val session = client.refreshSession(refreshToken)

            // Then
            assertEquals("user-123", session.userId)
            assertEquals("new-id-token", session.idToken)
            assertEquals("new-refresh-token", session.refreshToken)
            assertEquals(3_600L, session.expiresInSeconds)
        }
    }

    @Test
    fun `rejects unsuccessful token refresh`() = runTest {
        // Given
        val engine = MockEngine {
            respond(
                content = """
                {
                  "error": {
                    "code": 400,
                    "message": "INVALID_REFRESH_TOKEN"
                  }
                }
                """.trimIndent(),
                status = HttpStatusCode.BadRequest,
                headers = headersOf(
                    HttpHeaders.ContentType,
                    ContentType.Application.Json.toString(),
                ),
            )
        }

        withAuthClient(engine) { client ->
            // When / Then
            val error = assertFailsWith<FirebaseAuthException> {
                client.refreshSession("invalid-refresh-token")
            }

            assertTrue(error.requiresSignIn)
        }
    }

    @Test
    fun `rejects refresh response without required session fields`() = runTest {
        // Given
        val engine = MockEngine {
            respond(
                content = """{"user_id":"user-123"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType,
                    ContentType.Application.Json.toString(),
                ),
            )
        }

        withAuthClient(engine) { client ->
            // When / Then
            assertFailsWith<JsonConvertException> {
                client.refreshSession("test-refresh-token")
            }
        }
    }

    private suspend fun withAuthClient(engine: MockEngine, block: suspend (FirebaseAuthClient) -> Unit) {
        val httpClient = createFirebaseHttpClient(engine)

        try {
            block(
                FirebaseAuthClient(
                    httpClient = httpClient,
                    config = config,
                ),
            )
        } finally {
            httpClient.close()
            engine.close()
        }
    }
}
