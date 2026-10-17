package com.theunderseer.simplio.shared.firebase

import com.theunderseer.simplio.shared.firebase.auth.FirebaseSessionManager
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode

class FirestoreAccessChecker(
    private val httpClient: HttpClient,
    private val config: FirebaseConfig,
    private val sessionManager: FirebaseSessionManager,
) {
    suspend fun checkAccess() {
        val session = sessionManager.getValidSession()
        val response = httpClient.get(
            "https://firestore.googleapis.com/v1/projects/" +
                "${config.projectId}/databases/(default)/documents/" +
                "users/${session.userId}/students",
        ) {
            bearerAuth(session.idToken)

            url {
                parameters.append("pageSize", "1")
            }
        }

        check(response.status == HttpStatusCode.OK) {
            "Firestore access check failed: HTTP ${response.status.value}"
        }
    }
}
