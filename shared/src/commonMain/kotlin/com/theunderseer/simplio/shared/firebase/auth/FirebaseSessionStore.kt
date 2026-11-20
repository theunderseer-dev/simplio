package com.theunderseer.simplio.shared.firebase.auth

interface FirebaseSessionStore {
    /** Reads saved credentials without prompting the user. */
    suspend fun read(): RememberedFirebaseSession?
    suspend fun write(session: RememberedFirebaseSession, allowUserInteraction: Boolean)
    suspend fun clear(allowUserInteraction: Boolean)
}

class RememberedFirebaseSession(val userId: String, val refreshToken: String)

class FirebaseSessionStorageException(cause: Throwable? = null) :
    IllegalStateException("Unable to access saved sign-in", cause)
