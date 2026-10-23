package com.theunderseer.simplio.feature.attendance.data.remote

import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomUploadStore
import com.theunderseer.simplio.feature.attendance.data.sync.UploadSnapshot
import com.theunderseer.simplio.feature.attendance.fixtures.seedSyncDefaults
import com.theunderseer.simplio.feature.attendance.fixtures.testSyncDatabase
import com.theunderseer.simplio.feature.attendance.fixtures.testUploadTarget
import com.theunderseer.simplio.shared.firebase.FirebaseConfig
import com.theunderseer.simplio.shared.firebase.FirestoreFailure
import com.theunderseer.simplio.shared.firebase.FirestoreFailureKind
import com.theunderseer.simplio.shared.firebase.FirestoreRestClient
import com.theunderseer.simplio.shared.firebase.auth.FirebaseAuthApi
import com.theunderseer.simplio.shared.firebase.auth.FirebaseSession
import com.theunderseer.simplio.shared.firebase.auth.FirebaseSessionManager
import com.theunderseer.simplio.shared.firebase.createFirebaseHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FirestoreScheduleProtocolTest {
    private val version = "2026-10-21T00:00:00Z"
    private val headers = headersOf("Content-Type", "application/json")

    @Test
    fun `head accepts versions one through three and rejects unknown protocol before reading data`() = runTest {
        listOf(1, 2, 3, 4).forEach { protocol ->
            withClient(
                MockEngine {
                    respond(
                        JsonArray(
                            listOf(
                                buildJsonObject {
                                    put("readTime", version)
                                    put(
                                        "found",
                                        buildJsonObject {
                                            put(
                                                "name",
                                                "projects/test-project/databases/(default)/documents/users/owner/syncState/attendance",
                                            )
                                            put("updateTime", version)
                                            put(
                                                "fields",
                                                buildJsonObject {
                                                    put("protocolVersion", FirestoreUploadProtocol.integer(protocol))
                                                    put("batchId", FirestoreUploadProtocol.string("test-batch"))
                                                },
                                            )
                                        },
                                    )
                                },
                            ),
                        ).toString(),
                        headers = headers,
                    )
                },
            ) { client ->
                val remote = FirestoreAttendancePullRemote(client)
                if (protocol <= 3) {
                    assertEquals(version, remote.readHead().updateTime)
                } else {
                    assertEquals(
                        FirestoreFailureKind.INVALID_RESPONSE,
                        assertFailsWith<FirestoreFailure> {
                            remote.readHead()
                        }.kind,
                    )
                }
            }
        }
    }

    @Test
    fun `old frozen batch without protocol field recovers its version one receipt`() = runTest {
        val database = testSyncDatabase()
        try {
            database.seedSyncDefaults()
            val store = RoomUploadStore(database)
            val prepared = store.snapshot(assertNotNull(store.prepare(testUploadTarget)))
            val old = prepared.copy(protocolVersion = 1)
            val json = Json.encodeToString(old)
            assertTrue("protocolVersion" !in json)
            val restored = Json.decodeFromString<UploadSnapshot>(json)
            assertEquals(1, restored.protocolVersion)
            val body = FirestoreUploadProtocol.commitBody(restored)
            val documents = mutableMapOf<String, JsonObject>()
            var commits = 0
            withClient(
                MockEngine { request ->
                    if (request.method.value == "POST") {
                        commits++
                        Json.parseToJsonElement((request.body as TextContent).text).jsonObject.getValue("writes")
                            .jsonArray.forEach {
                                val update = it.jsonObject.getValue("update").jsonObject
                                documents[update.getValue("name").jsonPrimitive.content] =
                                    update.getValue("fields").jsonObject
                            }
                        throw FirestoreFailure(FirestoreFailureKind.RETRYABLE)
                    }
                    val name = "projects/test-project/databases/(default)/documents/" +
                        request.url.segments.dropWhile { it != "users" }.joinToString("/")
                    val fields = documents[name]
                    if (fields == null) {
                        respond("{}", HttpStatusCode.NotFound, headers)
                    } else {
                        respond(
                            buildJsonObject {
                                put("name", name)
                                put("fields", fields)
                                put("updateTime", version)
                            }.toString(),
                            headers = headers,
                        )
                    }
                },
            ) { client ->
                assertFailsWith<FirestoreFailure> {
                    FirestoreAttendanceUploadRemote(client).sendOrRecover(restored, body)
                }
                val acknowledgement = FirestoreAttendanceUploadRemote(client).sendOrRecover(restored, body)
                assertEquals(1, commits)
                assertEquals(restored.documents.size, acknowledgement.documentUpdateTimes.size)
                assertEquals(version, acknowledgement.headUpdateTime)
            }
        } finally {
            database.close()
        }
    }

    private suspend fun withClient(engine: MockEngine, block: suspend (FirestoreRestClient) -> Unit) {
        val sessions = FirebaseSessionManager(object : FirebaseAuthApi {
            override suspend fun signIn(email: String, password: String) =
                FirebaseSession("owner", "token", "refresh", 3600)
            override suspend fun refreshSession(refreshToken: String) =
                FirebaseSession("owner", "token", "refresh", 3600)
        })
        sessions.signIn("test@example.com", "test-password")
        val http = createFirebaseHttpClient(engine)
        try {
            block(FirestoreRestClient(http, FirebaseConfig("test-key", "test-project"), sessions, "owner"))
        } finally {
            http.close()
        }
    }
}
