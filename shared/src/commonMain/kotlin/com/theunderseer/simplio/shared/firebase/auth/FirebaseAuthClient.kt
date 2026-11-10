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
    override suspend fun signIn(email: String, password: String): FirebaseSession =
        passwordSession("signInWithPassword", email, password)

    suspend fun createAccount(email: String, password: String): FirebaseSession =
        passwordSession("signUp", email, password)

    suspend fun sendVerificationEmail(session: FirebaseSession) {
        postAccount("sendOobCode", VerificationEmailRequest("VERIFY_EMAIL", session.idToken))
    }

    suspend fun isEmailVerified(session: FirebaseSession): Boolean {
        val result = postAccount("lookup", LookupRequest(session.idToken)).body<LookupResponse>()
        return checkNotNull(result.users.singleOrNull { it.localId == session.userId }) {
            "Firebase did not return the signed-in account"
        }.emailVerified
    }

    suspend fun sendPasswordResetEmail(email: String) {
        try {
            postAccount("sendOobCode", PasswordResetRequest("PASSWORD_RESET", email))
        } catch (error: FirebaseAuthException) {
            // Older project configurations may disclose that the email does not exist.
            if (error.errorCode != "EMAIL_NOT_FOUND") throw error
        }
    }

    private suspend fun passwordSession(action: String, email: String, password: String): FirebaseSession {
        val result = postAccount(action, SignInRequest(email, password, true)).body<SignInResponse>()

        return FirebaseSession(
            userId = result.localId,
            idToken = result.idToken,
            refreshToken = result.refreshToken,
            expiresInSeconds = result.expiresIn.toLong(),
        )
    }

    private suspend inline fun <reified T : Any> postAccount(action: String, payload: T): HttpResponse {
        val response = httpClient.post("https://identitytoolkit.googleapis.com/v1/accounts:$action") {
            url { parameters.append("key", config.apiKey) }
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        if (!response.status.isSuccess()) throw response.toFirebaseAuthException()
        return response
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
private class VerificationEmailRequest(val requestType: String, val idToken: String)

@Serializable
private class PasswordResetRequest(val requestType: String, val email: String)

@Serializable
private class LookupRequest(val idToken: String)

@Serializable
private class LookupResponse(val users: List<LookupUser>)

@Serializable
private class LookupUser(val localId: String, val emailVerified: Boolean = false)

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
        errorCode = errorCode,
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
