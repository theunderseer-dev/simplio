package com.theunderseer.simplio.shared.firebase.auth

import com.theunderseer.simplio.shared.firebase.FirebaseConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

class FirebaseAuthClient(private val httpClient: HttpClient, private val config: FirebaseConfig) {
    suspend fun signIn(email: String, password: String): FirebaseSession {
        val response = httpClient.post(
            "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword",
        ) {
            url {
                parameters.append("key", config.apiKey)
            }

            contentType(ContentType.Application.Json)

            setBody(
                SignInRequest(
                    email = email,
                    password = password,
                    returnSecureToken = true,
                ),
            )
        }

        if (!response.status.isSuccess()) {
            throw IllegalStateException(
                "Firebase sign-in failed: HTTP ${response.status.value}",
            )
        }

        val result = response.body<SignInResponse>()

        return FirebaseSession(
            userId = result.localId,
            idToken = result.idToken,
            refreshToken = result.refreshToken,
            expiresInSeconds = result.expiresIn.toLong(),
        )
    }
}

@Serializable
private class SignInRequest(val email: String, val password: String, val returnSecureToken: Boolean)

@Serializable
private class SignInResponse(val localId: String, val idToken: String, val refreshToken: String, val expiresIn: String)
