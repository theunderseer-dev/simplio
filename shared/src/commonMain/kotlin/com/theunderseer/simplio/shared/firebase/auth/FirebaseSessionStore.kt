package com.theunderseer.simplio.shared.firebase.auth

interface FirebaseSessionStore {
    suspend fun read(): RememberedFirebaseSession?
    suspend fun write(session: RememberedFirebaseSession)
    suspend fun clear()
}

class RememberedFirebaseSession(val userId: String, val refreshToken: String)

class FirebaseSessionStorageException(cause: Throwable? = null) :
    IllegalStateException("Unable to access saved sign-in", cause)
