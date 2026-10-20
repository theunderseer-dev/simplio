package com.theunderseer.simplio.feature.attendance.data.remote

import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomSyncOutbox
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomUploadStore
import com.theunderseer.simplio.feature.attendance.data.sync.AttendanceUploadCoordinator
import com.theunderseer.simplio.feature.attendance.data.sync.AttendanceUploadStatus
import com.theunderseer.simplio.feature.attendance.data.sync.UploadProblem
import com.theunderseer.simplio.feature.attendance.data.sync.UploadTarget
import com.theunderseer.simplio.feature.attendance.fixtures.registerSyncStudent
import com.theunderseer.simplio.feature.attendance.fixtures.testSyncDatabase
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
import org.junit.Assume.assumeTrue
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FirestoreUploadEmulatorTest {
    @Test
    fun `second database cannot overwrite existing cloud head`() = runTest {
        withEmulator { client, target ->
            val first = testSyncDatabase()
            val second = testSyncDatabase()
            try {
                first.registerSyncStudent()
                second.registerSyncStudent()
                val remote = FirestoreAttendanceUploadRemote(client)
                assertEquals(
                    AttendanceUploadStatus.Uploading,
                    AttendanceUploadCoordinator(RoomUploadStore(first), remote, target).uploadNext(),
                )
                val store = RoomUploadStore(second)
                val batch = assertNotNull(store.prepare(target))
                assertEquals(
                    AttendanceUploadStatus.Paused(UploadProblem.CONFLICT),
                    AttendanceUploadCoordinator(store, remote, target).uploadNext(),
                )
                assertNull(client.getDocument(FirestoreUploadProtocol.RECEIPT_COLLECTION, batch.batchId))
                assertEquals(8, RoomSyncOutbox(second).getPending().size)
            } finally {
                first.close()
                second.close()
            }
        }
    }

    @Test
    fun `failed document precondition rolls back entire commit`() = runTest {
        withEmulator { client, target ->
            val database = testSyncDatabase()
            try {
                database.registerSyncStudent()
                val store = RoomUploadStore(database)
                val remote = FirestoreAttendanceUploadRemote(client)
                AttendanceUploadCoordinator(store, remote, target).uploadNext()
                val originalHead = client.getDocument(
                    FirestoreUploadProtocol.HEAD_COLLECTION,
                    FirestoreUploadProtocol.HEAD_ID,
                )
                val snapshot = store.snapshot(assertNotNull(store.prepare(target)))
                val last = snapshot.documents.last()
                val bad = snapshot.copy(
                    documents = snapshot.documents.dropLast(1) +
                        last.copy(change = last.change.copy(serverUpdateTime = "2000-01-01T00:00:00Z")),
                )
                assertEquals(
                    FirestoreFailureKind.CONFLICT,
                    assertFailsWith<FirestoreFailure> {
                        remote.sendOrRecover(bad, FirestoreUploadProtocol.commitBody(bad))
                    }.kind,
                )
                snapshot.documents.forEach {
                    assertNull(
                        client.getDocument(
                            FirestoreUploadProtocol.collection(it.change.entityType),
                            it.change.cloudId,
                        ),
                    )
                }
                assertNull(client.getDocument(FirestoreUploadProtocol.RECEIPT_COLLECTION, snapshot.batchId))
                assertEquals(
                    originalHead,
                    client.getDocument(FirestoreUploadProtocol.HEAD_COLLECTION, FirestoreUploadProtocol.HEAD_ID),
                )
            } finally {
                database.close()
            }
        }
    }

    private suspend fun withEmulator(block: suspend (FirestoreRestClient, UploadTarget) -> Unit) {
        val host = System.getenv("FIRESTORE_EMULATOR_HOST")
        assumeTrue("Set FIRESTORE_EMULATOR_HOST to run emulator integration tests", host != null)
        require(checkNotNull(host).matches(Regex("(127\\.0\\.0\\.1|localhost):[0-9]+")))
        val uid = "emulator-${UUID.randomUUID()}"
        val target = UploadTarget("demo-simplio-upload", uid)
        val sessions = FirebaseSessionManager(object : FirebaseAuthApi {
            override suspend fun signIn(email: String, password: String) =
                FirebaseSession(uid, "test-token", "refresh", 3600)
            override suspend fun refreshSession(refreshToken: String) =
                FirebaseSession(uid, "test-token", "refresh", 3600)
        })
        sessions.signIn("test@example.com", "test-password")
        val bridge = HttpClient.newBuilder().proxy(object : ProxySelector() {
            override fun select(uri: URI): List<Proxy> = listOf(Proxy.NO_PROXY)
            override fun connectFailed(uri: URI, sa: SocketAddress, ioe: java.io.IOException) = Unit
        }).build()
        val engine = MockEngine { request ->
            val builder = HttpRequest.newBuilder(URI("http://$host${request.url.encodedPath}"))
                .header("Authorization", "Bearer owner").header("Content-Type", "application/json")
            val message = if (request.method.value == "POST") {
                builder.POST(HttpRequest.BodyPublishers.ofString((request.body as TextContent).text)).build()
            } else {
                builder.GET().build()
            }
            val response = bridge.send(message, HttpResponse.BodyHandlers.ofString())
            respond(
                response.body(),
                HttpStatusCode.fromValue(response.statusCode()),
                headersOf("Content-Type", "application/json"),
            )
        }
        val http = createFirebaseHttpClient(engine)
        try {
            block(FirestoreRestClient(http, FirebaseConfig("test-key", target.projectId), sessions, uid), target)
        } finally {
            http.close()
        }
    }
}
