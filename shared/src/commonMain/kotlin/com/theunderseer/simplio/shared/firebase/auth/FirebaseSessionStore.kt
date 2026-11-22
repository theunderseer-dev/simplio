package com.theunderseer.simplio.shared.firebase.auth

interface FirebaseSessionStore {
    /** Automatic reads are silent. An explicit retry can authorize platform interaction. */
    suspend fun read(allowUserInteraction: Boolean = false): RememberedFirebaseSession?
    suspend fun write(session: RememberedFirebaseSession, allowUserInteraction: Boolean)
    suspend fun clear(allowUserInteraction: Boolean)
}

class RememberedFirebaseSession(val userId: String, val refreshToken: String)

enum class FirebaseSessionStorageOperation { Read, Write, Clear }

class FirebaseSessionStorageException(
    cause: Throwable? = null,
    val operation: FirebaseSessionStorageOperation? = null,
    val status: Int? = null,
    val isTemporarilyUnavailable: Boolean = false,
) : IllegalStateException("Unable to access saved sign-in", cause)
