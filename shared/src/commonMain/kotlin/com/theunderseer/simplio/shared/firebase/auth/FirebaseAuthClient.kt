package com.theunderseer.simplio.shared.firebase.auth

import com.theunderseer.simplio.shared.firebase.FirebaseConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.coroutines.cancellation.CancellationException

class FirebaseAuthClient(private val httpClient: HttpClient, private val config: FirebaseConfig) : FirebaseAuthApi {
    override suspend fun signIn(email: String, password: String): FirebaseSession {
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
            throw response.toFirebaseAuthException()
        }

        val result = response.body<SignInResponse>()

        return FirebaseSession(
            userId = result.localId,
            idToken = result.idToken,
            refreshToken = result.refreshToken,
            expiresInSeconds = result.expiresIn.toLong(),
        )
    }

    override suspend fun refreshSession(refreshToken: String): FirebaseSession {
        val response = httpClient.submitForm(
            url = "https://securetoken.googleapis.com/v1/token",
            formParameters = parameters {
                append("grant_type", "refresh_token")
                append("refresh_token", refreshToken)
            },
        ) {
            url {
                parameters.append("key", config.apiKey)
            }
        }

        if (!response.status.isSuccess()) {
            throw response.toFirebaseAuthException()
        }

        val result = response.body<RefreshSessionResponse>()

        return FirebaseSession(
            userId = result.userId,
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

@Serializable
private class RefreshSessionResponse(
    @SerialName("user_id")
    val userId: String,
    @SerialName("id_token")
    val idToken: String,
    @SerialName("refresh_token")
    val refreshToken: String,
    @SerialName("expires_in")
    val expiresIn: String,
)

private suspend fun HttpResponse.toFirebaseAuthException(): FirebaseAuthException {
    val errorCode = try {
        body<FirebaseErrorResponse>()
            .error
            .message
            .substringBefore(" : ")
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    return FirebaseAuthException(
        statusCode = status.value,
        requiresSignIn = errorCode in setOf(
            "TOKEN_EXPIRED",
            "USER_DISABLED",
            "USER_NOT_FOUND",
            "INVALID_REFRESH_TOKEN",
        ),
    )
}

@Serializable
private class FirebaseErrorResponse(val error: FirebaseError)

@Serializable
private class FirebaseError(val message: String)
