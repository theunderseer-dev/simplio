package com.theunderseer.simplio.feature.attendance.data.remote

import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentRepository
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomSyncOutbox
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomUploadStore
import com.theunderseer.simplio.feature.attendance.data.sync.AttendanceUploadCoordinator
import com.theunderseer.simplio.feature.attendance.data.sync.AttendanceUploadStatus
import com.theunderseer.simplio.feature.attendance.data.sync.UploadProblem
import com.theunderseer.simplio.feature.attendance.fixtures.acknowledgeCatalog
import com.theunderseer.simplio.feature.attendance.fixtures.registerSyncStudent
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FirestoreAttendanceUploadRemoteTest {
    private val version = "2026-10-19T12:00:00.123456Z"
    private val headers = headersOf("Content-Type", "application/json")

    @Test
    fun `lost response recovers receipt after restart and preserves newer edit`() = runTest {
        val database = testSyncDatabase()
        try {
            val student = database.registerSyncStudent().student
            val store = RoomUploadStore(database)
            store.acknowledgeCatalog()
            val documents = mutableMapOf<String, JsonObject>()
            var commits = 0
            val engine = MockEngine { request ->
                if (request.method.value == "POST") {
                    commits++
                    val writes = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                        .getValue("writes").jsonArray
                    writes.forEach {
                        val update = it.jsonObject.getValue("update").jsonObject
                        documents[update.getValue("name").jsonPrimitive.content] = update.getValue("fields").jsonObject
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
            }
            withRemote(engine) { remote ->
                val first = AttendanceUploadCoordinator(store, remote, testUploadTarget)
                assertIs<AttendanceUploadStatus.WaitingToRetry>(first.uploadNext())
                val frozen = assertNotNull(database.syncUploadDao().getBatch())
                RoomStudentRepository(database).updateNote(student.id, "newer note")
                val restarted = AttendanceUploadCoordinator(RoomUploadStore(database), remote, testUploadTarget)
                assertEquals(AttendanceUploadStatus.Uploading, restarted.uploadNext())
                assertEquals(1, commits)
                assertEquals(1, RoomSyncOutbox(database).getPending().size)
                assertNull(database.syncUploadDao().getBatch())
                assertEquals(false, frozen.commitBody.contains("newer note"))
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun `receipt cannot acknowledge when cloud head has advanced`() = runTest {
        val database = testSyncDatabase()
        try {
            database.registerSyncStudent()
            val store = RoomUploadStore(database)
            val snapshot = store.snapshot(assertNotNull(store.prepare(testUploadTarget)))
            var posts = 0
            withRemote(
                MockEngine { request ->
                    if (request.method.value == "POST") posts++
                    val name = "projects/test-project/databases/(default)/documents/" +
                        request.url.segments.dropWhile { it != "users" }.joinToString("/")
                    val fields = if (name.contains("syncReceipts")) {
                        FirestoreUploadProtocol.receiptFields(snapshot)
                    } else {
                        buildJsonObject {
                            put("batchId", FirestoreUploadProtocol.string("another-batch"))
                        }
                    }
                    respond(
                        buildJsonObject {
                            put("name", name)
                            put("fields", fields)
                            put("updateTime", version)
                        }.toString(),
                        headers = headers,
                    )
                },
            ) { remote ->
                val result = AttendanceUploadCoordinator(store, remote, testUploadTarget).uploadNext()
                assertEquals(AttendanceUploadStatus.Paused(UploadProblem.CONFLICT), result)
                assertEquals(0, posts)
                assertEquals(8, RoomSyncOutbox(database).getPending().size)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun `new catalog commit includes guarded documents head and receipt`() = runTest {
        val database = testSyncDatabase()
        try {
            database.registerSyncStudent()
            var posts = 0
            withRemote(
                MockEngine { request ->
                    if (request.method.value !=
                        "POST"
                    ) {
                        return@MockEngine respond("{}", HttpStatusCode.NotFound, headers)
                    }
                    posts++
                    val writes = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                        .getValue("writes").jsonArray
                    assertEquals(7, writes.size)
                    writes.forEach {
                        assertEquals(
                            "false",
                            it.jsonObject.getValue("currentDocument").jsonObject
                                .getValue("exists").jsonPrimitive.content,
                        )
                        val name = it.jsonObject.getValue("update").jsonObject.getValue("name").jsonPrimitive.content
                        assertEquals(true, name.contains("/users/owner/"))
                    }
                    assertEquals(true, writes[5].toString().contains("syncState/attendance"))
                    assertEquals(true, writes[6].toString().contains("syncReceipts/"))
                    respond(
                        "{\"writeResults\":[" + List(7) { "{\"updateTime\":\"$version\"}" }.joinToString(",") + "]}",
                        headers = headers,
                    )
                },
            ) { remote ->
                assertEquals(
                    AttendanceUploadStatus.Uploading,
                    AttendanceUploadCoordinator(RoomUploadStore(database), remote, testUploadTarget).uploadNext(),
                )
                assertEquals(1, posts)
                assertEquals(3, RoomSyncOutbox(database).getPending().size)
            }
        } finally {
            database.close()
        }
    }

    private suspend fun withRemote(engine: MockEngine, block: suspend (FirestoreAttendanceUploadRemote) -> Unit) {
        val sessions = FirebaseSessionManager(object : FirebaseAuthApi {
            override suspend fun signIn(email: String, password: String) =
                FirebaseSession("owner", "id-token", "refresh", 3600)
            override suspend fun refreshSession(refreshToken: String) =
                FirebaseSession("owner", "id-token", "refresh", 3600)
        })
        sessions.signIn("test@example.com", "test-password")
        val http = createFirebaseHttpClient(engine)
        try {
            block(
                FirestoreAttendanceUploadRemote(
                    FirestoreRestClient(
                        http,
                        FirebaseConfig("key", "test-project"),
                        sessions,
                        "owner",
                    ),
                ),
            )
        } finally {
            http.close()
        }
    }
}
