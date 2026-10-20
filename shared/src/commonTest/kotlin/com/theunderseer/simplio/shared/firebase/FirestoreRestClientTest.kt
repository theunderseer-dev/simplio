package com.theunderseer.simplio.shared.firebase

import com.theunderseer.simplio.shared.firebase.auth.FirebaseAuthApi
import com.theunderseer.simplio.shared.firebase.auth.FirebaseSession
import com.theunderseer.simplio.shared.firebase.auth.FirebaseSessionManager
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class FirestoreRestClientTest {
    private val version = "2026-10-19T12:00:00.123456Z"
    private val name = "projects/test-project/databases/(default)/documents/users/owner/students/student"
    private val body: String get() = """{"writes":[{"update":{"name":"$name","fields":{}}}]}"""

    @Test
    fun `commit sends exact body with current bearer token`() = runTest {
        val engine = MockEngine { request ->
            assertEquals(
                "https://firestore.googleapis.com/v1/projects/test-project/databases/(default)/documents:commit",
                request.url.toString(),
            )
            assertEquals("POST", request.method.value)
            assertEquals("Bearer id-token", request.headers["Authorization"])
            assertEquals(body, (request.body as TextContent).text)
            respond("""{"writeResults":[{"updateTime":"$version"}]}""", headers = jsonHeaders())
        }
        withClient(engine) { client, _ -> assertEquals(listOf(version), client.commit(body)) }
    }

    @Test
    fun `missing document returns null`() = runTest {
        withClient(MockEngine { respond("{}", HttpStatusCode.NotFound) }) { client, _ ->
            assertNull(client.getDocument("students", "missing"))
        }
    }

    @Test
    fun `document read preserves exact server version`() = runTest {
        withClient(
            MockEngine {
                respond("""{"name":"$name","fields":{},"updateTime":"$version"}""", headers = jsonHeaders())
            },
        ) { client, _ -> assertEquals(version, client.getDocument("students", "student")?.updateTime) }
    }

    @Test
    fun `canonical precondition status is treated as conflict`() = runTest {
        withClient(
            MockEngine {
                respond("""{"error":{"status":"FAILED_PRECONDITION"}}""", HttpStatusCode.BadRequest, jsonHeaders())
            },
        ) { client, _ ->
            assertEquals(FirestoreFailureKind.CONFLICT, assertFailsWith<FirestoreFailure> { client.commit(body) }.kind)
        }
    }

    @Test
    fun `http failures distinguish authorization retry and rejection`() = runTest {
        val cases = listOf(
            401 to FirestoreFailureKind.AUTHENTICATION,
            403 to FirestoreFailureKind.PERMISSION,
            429 to FirestoreFailureKind.RETRYABLE,
            503 to FirestoreFailureKind.RETRYABLE,
            500 to FirestoreFailureKind.INTERNAL,
            400 to FirestoreFailureKind.REJECTED,
        )
        cases.forEach { (status, kind) ->
            withClient(MockEngine { respond("{}", HttpStatusCode.fromValue(status), jsonHeaders()) }) { client, _ ->
                assertEquals(kind, assertFailsWith<FirestoreFailure> { client.commit(body) }.kind)
            }
        }
    }

    @Test
    fun `malformed success cannot acknowledge mutations`() = runTest {
        withClient(MockEngine { respond("""{"writeResults":[]}""", headers = jsonHeaders()) }) { client, _ ->
            assertEquals(
                FirestoreFailureKind.INVALID_RESPONSE,
                assertFailsWith<FirestoreFailure> { client.commit(body) }.kind,
            )
        }
    }

    @Test
    fun `cross account write is rejected before network request`() = runTest {
        var calls = 0
        withClient(
            MockEngine {
                calls++
                respond("{}")
            },
        ) { client, _ ->
            assertFailsWith<IllegalArgumentException> { client.commit(body.replace("users/owner/", "users/other/")) }
            assertEquals(0, calls)
        }
    }

    @Test
    fun `different signed in user cancels bound client`() = runTest {
        var calls = 0
        withClient(
            MockEngine {
                calls++
                respond("{}")
            },
            "other",
        ) { client, _ ->
            assertFailsWith<CancellationException> { client.commit(body) }
            assertEquals(0, calls)
        }
    }

    @Test
    fun `sign out during request prevents acknowledgement`() = runTest {
        lateinit var sessions: FirebaseSessionManager
        withClient(
            MockEngine {
                sessions.signOut()
                respond("""{"writeResults":[{"updateTime":"$version"}]}""", headers = jsonHeaders())
            },
        ) { client, manager ->
            sessions = manager
            assertFailsWith<CancellationException> { client.commit(body) }
        }
    }

    private fun jsonHeaders() = headersOf("Content-Type", ContentType.Application.Json.toString())

    private suspend fun withClient(
        engine: MockEngine,
        userId: String = "owner",
        block: suspend (FirestoreRestClient, FirebaseSessionManager) -> Unit,
    ) {
        val sessions = FirebaseSessionManager(object : FirebaseAuthApi {
            override suspend fun signIn(email: String, password: String) =
                FirebaseSession(userId, "id-token", "refresh", 3600)
            override suspend fun refreshSession(refreshToken: String) =
                FirebaseSession(userId, "id-token", "refresh", 3600)
        })
        sessions.signIn("test@example.com", "test-password")
        val http = createFirebaseHttpClient(engine)
        try {
            block(FirestoreRestClient(http, FirebaseConfig("test-key", "test-project"), sessions, "owner"), sessions)
        } finally {
            http.close()
        }
    }
}
