package com.theunderseer.simplio.shared.firebase.auth

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class FirebaseSessionManager(
    private val authApi: FirebaseAuthApi,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val sessionStore: FirebaseSessionStore? = null,
) {
    private val stateMutex = Mutex()
    private val refreshMutex = Mutex()

    private val mutableSession = MutableStateFlow<FirebaseSession?>(null)

    val session: StateFlow<FirebaseSession?> = mutableSession.asStateFlow()

    val canRememberSession: Boolean get() = sessionStore != null

    private var remembered = false
    private var refreshAt: TimeMark? = null
    private var revision = 0L

    suspend fun signIn(
        email: String,
        password: String,
        rememberSession: Boolean = false,
        validate: suspend (FirebaseSession) -> Unit = {},
    ): FirebaseSession {
        val operationRevision = stateMutex.withLock {
            clearSession()
            revision
        }
        val requestStartedAt = timeSource.markNow()
        val candidate = authApi.signIn(email, password)
        validate(candidate)
        return activate(candidate, requestStartedAt, operationRevision, rememberSession)
    }

    suspend fun restoreSession(validate: suspend (FirebaseSession) -> Unit = {}): FirebaseSession? {
        val store = sessionStore ?: return null
        val (operationRevision, saved) = stateMutex.withLock {
            if (mutableSession.value != null) return null
            val saved = store.read() ?: return null
            clearSession()
            revision to saved
        }

        val requestStartedAt = timeSource.markNow()
        val candidate = try {
            authApi.refreshSession(saved.refreshToken)
        } catch (error: FirebaseAuthException) {
            if (!error.requiresSignIn) throw error
            forgetFailedRestore(operationRevision)
            return null
        }
        if (candidate.userId != saved.userId) {
            forgetFailedRestore(operationRevision)
            return null
        }
        validate(candidate)
        return activate(candidate, requestStartedAt, operationRevision, rememberSession = true)
    }

    suspend fun signOut() {
        stateMutex.withLock {
            withContext(NonCancellable) {
                sessionStore?.clear()
                clearSession()
            }
        }
    }

    private suspend fun forgetFailedRestore(operationRevision: Long) {
        stateMutex.withLock {
            if (revision == operationRevision) sessionStore?.clear()
        }
    }

    private suspend fun activate(
        candidate: FirebaseSession,
        requestStartedAt: TimeMark,
        operationRevision: Long,
        rememberSession: Boolean,
    ): FirebaseSession = stateMutex.withLock {
        currentCoroutineContext().ensureActive()
        if (revision != operationRevision) throw CancellationException("Authentication state changed")
        require(candidate.expiresInSeconds > 0) { "Session lifetime must be positive" }

        val store = sessionStore
        if (store != null) {
            if (rememberSession) {
                store.write(RememberedFirebaseSession(candidate.userId, candidate.refreshToken))
            } else {
                store.clear()
            }
        }
        remembered = rememberSession && store != null
        installSession(candidate, requestStartedAt)
        candidate
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
                            val shouldForget = remembered
                            clearSession()
                            if (shouldForget) sessionStore?.clear()
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

                if (remembered && refreshedSession.refreshToken != snapshot.session.refreshToken) {
                    try {
                        sessionStore?.write(
                            RememberedFirebaseSession(refreshedSession.userId, refreshedSession.refreshToken),
                        )
                    } catch (_: FirebaseSessionStorageException) {
                        // Keep the valid in-memory session if Keychain is temporarily unavailable.
                    }
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
        remembered = false
        refreshAt = null
        mutableSession.value = null
    }

    private fun isCurrent(snapshot: SessionSnapshot): Boolean = revision == snapshot.revision &&
        mutableSession.value === snapshot.session

    private class SessionSnapshot(val session: FirebaseSession, val revision: Long)
}
