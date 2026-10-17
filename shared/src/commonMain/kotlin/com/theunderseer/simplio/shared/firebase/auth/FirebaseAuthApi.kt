package com.theunderseer.simplio.shared.firebase.auth

interface FirebaseAuthApi {
    suspend fun signIn(email: String, password: String): FirebaseSession

    suspend fun refreshSession(refreshToken: String): FirebaseSession
}

class FirebaseAuthException(val statusCode: Int, val requiresSignIn: Boolean) :
    IllegalStateException(
        "Firebase authentication failed: HTTP $statusCode",
    )
