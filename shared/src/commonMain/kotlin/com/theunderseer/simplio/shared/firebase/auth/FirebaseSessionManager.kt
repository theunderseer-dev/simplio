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

    private var persistedRefreshToken: String? = null
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
        return stateMutex.withLock {
            checkActivation(candidate, operationRevision)
            val store = sessionStore
            if (store != null && rememberSession) {
                store.write(
                    RememberedFirebaseSession(candidate.userId, candidate.refreshToken),
                    allowUserInteraction = true,
                )
                persistedRefreshToken = candidate.refreshToken
            } else {
                store?.clear(allowUserInteraction = true)
            }
            installSession(candidate, requestStartedAt)
            candidate
        }
    }

    suspend fun restoreSession(
        allowUserInteraction: Boolean = false,
        validate: suspend (FirebaseSession) -> Unit = {},
    ): FirebaseSession? {
        val store = sessionStore ?: return null
        val (operationRevision, saved) = stateMutex.withLock {
            if (mutableSession.value != null) return null
            val saved = store.read(allowUserInteraction) ?: return null
            clearSession()
            revision to saved
        }

        val requestStartedAt = timeSource.markNow()
        val candidate = try {
            authApi.refreshSession(saved.refreshToken)
        } catch (error: FirebaseAuthException) {
            if (!error.requiresSignIn) throw error
            forgetFailedRestore(operationRevision, allowUserInteraction)
            return null
        }
        if (candidate.userId != saved.userId) {
            forgetFailedRestore(operationRevision, allowUserInteraction)
            return null
        }
        validate(candidate)
        return stateMutex.withLock {
            checkActivation(candidate, operationRevision)
            persistedRefreshToken = saved.refreshToken
            val writeFailure = persistRefreshTokenIfChanged(candidate, allowUserInteraction)
            if (writeFailure != null && !writeFailure.isTemporarilyUnavailable) throw writeFailure
            installSession(candidate, requestStartedAt)
            candidate
        }
    }

    suspend fun signOut() {
        stateMutex.withLock {
            withContext(NonCancellable) {
                sessionStore?.clear(allowUserInteraction = true)
                clearSession()
            }
        }
    }

    suspend fun retrySavingRememberedSession() {
        stateMutex.withLock {
            val current = mutableSession.value ?: return
            persistRefreshTokenIfChanged(current, allowUserInteraction = false)
        }
    }

    private suspend fun forgetFailedRestore(operationRevision: Long, allowUserInteraction: Boolean) {
        stateMutex.withLock {
            if (revision == operationRevision) sessionStore?.clear(allowUserInteraction)
        }
    }

    private suspend fun checkActivation(candidate: FirebaseSession, operationRevision: Long) {
        currentCoroutineContext().ensureActive()
        if (revision != operationRevision) throw CancellationException("Authentication state changed")
        require(candidate.expiresInSeconds > 0) { "Session lifetime must be positive" }
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
                            val shouldForget = persistedRefreshToken != null
                            clearSession()
                            if (shouldForget) {
                                try {
                                    sessionStore?.clear(allowUserInteraction = false)
                                } catch (storageError: FirebaseSessionStorageException) {
                                    error.addSuppressed(storageError)
                                }
                            }
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

                persistRefreshTokenIfChanged(refreshedSession, allowUserInteraction = false)
                installSession(refreshedSession, requestStartedAt)
                refreshedSession
            }
        }
    }

    private suspend fun persistRefreshTokenIfChanged(
        session: FirebaseSession,
        allowUserInteraction: Boolean,
    ): FirebaseSessionStorageException? {
        val store = sessionStore ?: return null
        val persisted = persistedRefreshToken ?: return null
        if (persisted == session.refreshToken) return null

        return try {
            store.write(RememberedFirebaseSession(session.userId, session.refreshToken), allowUserInteraction)
            persistedRefreshToken = session.refreshToken
            null
        } catch (error: FirebaseSessionStorageException) {
            // Keep the last persisted value so the latest token is retried on the next opportunity.
            error
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
        persistedRefreshToken = null
        refreshAt = null
        mutableSession.value = null
    }

    private fun isCurrent(snapshot: SessionSnapshot): Boolean = revision == snapshot.revision &&
        mutableSession.value === snapshot.session

    private class SessionSnapshot(val session: FirebaseSession, val revision: Long)
}
