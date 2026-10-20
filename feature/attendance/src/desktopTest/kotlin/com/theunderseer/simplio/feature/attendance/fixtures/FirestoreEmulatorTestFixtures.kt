package com.theunderseer.simplio.feature.attendance.fixtures

import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomConflictStore
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomPullStore
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomUploadStore
import com.theunderseer.simplio.feature.attendance.data.remote.FirestoreAttendancePullRemote
import com.theunderseer.simplio.feature.attendance.data.remote.FirestoreAttendanceUploadRemote
import com.theunderseer.simplio.feature.attendance.data.sync.AttendancePullRemote
import com.theunderseer.simplio.feature.attendance.data.sync.AttendanceSyncCoordinator
import com.theunderseer.simplio.feature.attendance.data.sync.AttendanceUploadCoordinator
import com.theunderseer.simplio.feature.attendance.data.sync.UploadTarget
import com.theunderseer.simplio.shared.firebase.FirebaseConfig
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
import org.junit.Assume.assumeTrue
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID

internal suspend fun withFirestoreEmulator(block: suspend (FirestoreRestClient, UploadTarget) -> Unit) {
    val host = System.getenv("FIRESTORE_EMULATOR_HOST")
    assumeTrue("Set FIRESTORE_EMULATOR_HOST to run emulator integration tests", host != null)
    require(checkNotNull(host).matches(Regex("(127\\.0\\.0\\.1|localhost):[0-9]+")))
    val uid = "emulator-${UUID.randomUUID()}"
    val target = UploadTarget("demo-simplio-upload", uid)
    val sessions = FirebaseSessionManager(object : FirebaseAuthApi {
        override suspend fun signIn(email: String, password: String) =
            FirebaseSession(uid, "test-token", "refresh", 3600)
        override suspend fun refreshSession(refreshToken: String) = FirebaseSession(uid, "test-token", "refresh", 3600)
    })
    sessions.signIn("test@example.com", "test-password")
    val bridge = HttpClient.newBuilder().proxy(object : ProxySelector() {
        override fun select(uri: URI): List<Proxy> = listOf(Proxy.NO_PROXY)
        override fun connectFailed(uri: URI, sa: SocketAddress, ioe: java.io.IOException) = Unit
    }).build()
    val engine = MockEngine { request ->
        val builder = HttpRequest.newBuilder(
            URI(
                "http://$host${request.url.encodedPath}" +
                    request.url.encodedQuery.takeIf { it.isNotEmpty() }?.let { "?$it" }.orEmpty(),
            ),
        )
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

internal fun testFirestoreSyncCoordinator(
    database: AttendanceDatabase,
    client: FirestoreRestClient,
    target: UploadTarget,
    remote: AttendancePullRemote = FirestoreAttendancePullRemote(client),
): AttendanceSyncCoordinator = AttendanceSyncCoordinator(
    store = RoomPullStore(database, target),
    remote = remote,
    uploader = AttendanceUploadCoordinator(RoomUploadStore(database), FirestoreAttendanceUploadRemote(client), target),
    conflicts = RoomConflictStore(database, target),
)
