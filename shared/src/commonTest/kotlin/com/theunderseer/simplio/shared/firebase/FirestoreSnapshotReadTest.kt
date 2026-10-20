package com.theunderseer.simplio.shared.firebase

import com.theunderseer.simplio.shared.firebase.auth.FirebaseAuthApi
import com.theunderseer.simplio.shared.firebase.auth.FirebaseSession
import com.theunderseer.simplio.shared.firebase.auth.FirebaseSessionManager
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class FirestoreSnapshotReadTest {
    private val time = "2026-10-20T12:00:01.123456Z"
    private val version = "2026-10-20T12:00:00.123456Z"
    private val base = "projects/test-project/databases/(default)/documents/users/owner"
    private val headers = headersOf("Content-Type", "application/json")

    @Test
    fun `head read captures server time and exact document version`() = runTest {
        withClient(
            MockEngine { request ->
                val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                assertEquals(
                    "$base/syncState/attendance",
                    body.getValue("documents").jsonArray.single().jsonPrimitive.content,
                )
                assertEquals("Bearer id-token", request.headers["Authorization"])
                respond(
                    """
                    [
                      {
                        "found": {
                          "name": "$base/syncState/attendance",
                          "fields": {},
                          "updateTime": "$version"
                        },
                        "readTime": "$time"
                      }
                    ]
                    """.trimIndent(),
                    headers = headers,
                )
            },
        ) { client ->
            val result = client.readDocumentSnapshot("syncState", "attendance")
            assertEquals(time, result.readTime)
            assertEquals(version, result.document?.updateTime)
        }
    }

    @Test
    fun `missing head still returns server read time`() = runTest {
        withClient(
            MockEngine {
                respond("""[{"missing":"$base/syncState/attendance","readTime":"$time"}]""", headers = headers)
            },
        ) {
            val result = it.readDocumentSnapshot("syncState", "attendance")
            assertNull(result.document)
            assertEquals(time, result.readTime)
        }
    }

    @Test
    fun `collection page pins time and continues after document cursor`() = runTest {
        var calls = 0
        withClient(
            MockEngine { request ->
                calls++
                assertEquals(true, request.url.toString().endsWith("/users/owner:runQuery"))
                val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                assertEquals(time, body.getValue("readTime").jsonPrimitive.content)
                val query = body.getValue("structuredQuery").jsonObject
                assertEquals("1", query.getValue("limit").jsonPrimitive.content)
                if (calls == 1) {
                    assertNull(query["startAt"])
                    respond(
                        """
                        [
                          {
                            "document": {
                              "name": "$base/students/a",
                              "fields": {},
                              "updateTime": "$version"
                            },
                            "readTime": "$time"
                          }
                        ]
                        """.trimIndent(),
                        headers = headers,
                    )
                } else {
                    val cursor = query.getValue("startAt").jsonObject
                    assertEquals("false", cursor.getValue("before").jsonPrimitive.content)
                    assertEquals(
                        "$base/students/a",
                        cursor.getValue("values").jsonArray.single().jsonObject
                            .getValue("referenceValue").jsonPrimitive.content,
                    )
                    respond("""[{"readTime":"$time"}]""", headers = headers)
                }
            },
        ) { client ->
            val first = client.queryDocumentsAt("students", time, pageSize = 1)
            assertEquals("a", first.nextAfterId)
            assertEquals("a", first.documents.single().id)
            val second = client.queryDocumentsAt("students", time, afterId = first.nextAfterId, pageSize = 1)
            assertEquals(emptyList(), second.documents)
            assertNull(second.nextAfterId)
        }
    }

    @Test
    fun `head response cannot cross account boundary`() = runTest {
        withClient(
            MockEngine {
                respond(
                    """
                    [
                      {
                        "missing": "projects/test-project/databases/(default)/documents/users/other/syncState/attendance",
                        "readTime": "$time"
                      }
                    ]
                    """.trimIndent(),
                    headers = headers,
                )
            },
        ) { client ->
            assertEquals(
                FirestoreFailureKind.INVALID_RESPONSE,
                assertFailsWith<FirestoreFailure> { client.readDocumentSnapshot("syncState", "attendance") }.kind,
            )
        }
    }

    @Test
    fun `query rejects foreign documents duplicate IDs and mismatched read time`() = runTest {
        val row = """
{
  "document": {
    "name": "$base/students/a",
    "fields": {},
    "updateTime": "$version"
  },
  "readTime": "$time"
}
        """.trimIndent()
        val bodies = listOf(
            "[$row,$row]",
            "[${row.replace("users/owner", "users/other")}]",
            "[${row.replace(time, "2026-10-20T12:00:02Z")}]",
            "[]",
        )
        bodies.forEach { body ->
            withClient(MockEngine { respond(body, headers = headers) }) { client ->
                assertEquals(
                    FirestoreFailureKind.INVALID_RESPONSE,
                    assertFailsWith<FirestoreFailure> { client.queryDocumentsAt("students", time) }.kind,
                )
            }
        }
    }

    @Test
    fun `invalid cursor is rejected before sending a query`() = runTest {
        var calls = 0
        withClient(
            MockEngine {
                calls++
                respond("[]", headers = headers)
            },
        ) { client ->
            assertFailsWith<IllegalArgumentException> { client.queryDocumentsAt("students", time, "../other") }
            assertEquals(0, calls)
        }
    }

    private suspend fun withClient(engine: MockEngine, block: suspend (FirestoreRestClient) -> Unit) {
        val sessions = FirebaseSessionManager(object : FirebaseAuthApi {
            override suspend fun signIn(email: String, password: String) =
                FirebaseSession("owner", "id-token", "refresh", 3600)
            override suspend fun refreshSession(refreshToken: String) =
                FirebaseSession("owner", "id-token", "refresh", 3600)
        })
        sessions.signIn("test@example.com", "test-password")
        val http = createFirebaseHttpClient(engine)
        try {
            block(FirestoreRestClient(http, FirebaseConfig("key", "test-project"), sessions, "owner"))
        } finally {
            http.close()
        }
    }
}
