package com.theunderseer.simplio.shared.firebase.auth

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class FirebaseSessionManager(
    private val authApi: FirebaseAuthApi,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private val stateMutex = Mutex()
    private val refreshMutex = Mutex()

    private val mutableSession = MutableStateFlow<FirebaseSession?>(null)

    val session: StateFlow<FirebaseSession?> = mutableSession.asStateFlow()

    private var refreshAt: TimeMark? = null
    private var revision = 0L

    suspend fun signIn(email: String, password: String): FirebaseSession {
        val operationRevision = stateMutex.withLock {
            clearSession()
            revision
        }

        val requestStartedAt = timeSource.markNow()

        val signedInSession = authApi.signIn(
            email = email,
            password = password,
        )

        return stateMutex.withLock {
            currentCoroutineContext().ensureActive()

            if (revision != operationRevision) {
                throw CancellationException("Authentication state changed")
            }

            installSession(signedInSession, requestStartedAt)
            signedInSession
        }
    }

    suspend fun signOut() {
        stateMutex.withLock {
            clearSession()
        }
    }

    suspend fun getValidSession(): FirebaseSession {
        return refreshMutex.withLock {
            val snapshot = stateMutex.withLock {
                val currentSession = checkNotNull(mutableSession.value) {
                    "Sign-in is required"
                }

                if (!checkNotNull(refreshAt).hasPassedNow()) {
                    return currentSession
                }

                SessionSnapshot(
                    session = currentSession,
                    revision = revision,
                )
            }

            val requestStartedAt = timeSource.markNow()

            val refreshedSession = try {
                authApi.refreshSession(snapshot.session.refreshToken)
            } catch (error: FirebaseAuthException) {
                if (error.requiresSignIn) {
                    stateMutex.withLock {
                        if (isCurrent(snapshot)) {
                            clearSession()
                        }
                    }
                }

                throw error
            }

            stateMutex.withLock {
                currentCoroutineContext().ensureActive()

                if (!isCurrent(snapshot)) {
                    throw CancellationException("Authentication state changed")
                }

                check(refreshedSession.userId == snapshot.session.userId) {
                    "Firebase returned a different user during token refresh"
                }

                installSession(refreshedSession, requestStartedAt)
                refreshedSession
            }
        }
    }

    private fun installSession(session: FirebaseSession, requestStartedAt: TimeMark) {
        require(session.expiresInSeconds > 0) {
            "Session lifetime must be positive"
        }

        val lifetime = session.expiresInSeconds.seconds
        val refreshMargin = minOf(60.seconds, lifetime / 2)

        refreshAt = requestStartedAt + (lifetime - refreshMargin)
        mutableSession.value = session
    }

    private fun clearSession() {
        revision += 1
        refreshAt = null
        mutableSession.value = null
    }

    private fun isCurrent(snapshot: SessionSnapshot): Boolean = revision == snapshot.revision &&
        mutableSession.value === snapshot.session

    private class SessionSnapshot(val session: FirebaseSession, val revision: Long)
}
