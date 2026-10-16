package com.theunderseer.simplio.shared.firebase.auth

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class FirebaseSessionManagerTest {
    private val initialSession = FirebaseSession(
        userId = "user-123",
        idToken = "initial-id-token",
        refreshToken = "initial-refresh-token",
        expiresInSeconds = 3_600,
    )

    private val refreshedSession = FirebaseSession(
        userId = "user-123",
        idToken = "new-id-token",
        refreshToken = "new-refresh-token",
        expiresInSeconds = 3_600,
    )

    @Test
    fun `returns valid session without refreshing`() = runTest {
        val api = FakeAuthApi()
        val timeSource = TestTimeSource()
        val manager = FirebaseSessionManager(api, timeSource)

        manager.signIn("owner@example.com", "test-password")
        timeSource += 3_539.seconds

        assertSame(initialSession, manager.getValidSession())
        assertEquals(emptyList(), api.requestedRefreshTokens)
    }

    @Test
    fun `parallel requests refresh session only once`() = runTest {
        val api = FakeAuthApi()
        val timeSource = TestTimeSource()
        val manager = FirebaseSessionManager(api, timeSource)
        val refreshStarted = CompletableDeferred<Unit>()
        val finishRefresh = CompletableDeferred<Unit>()

        api.refreshHandler = {
            refreshStarted.complete(Unit)
            finishRefresh.await()
            refreshedSession
        }

        manager.signIn("owner@example.com", "test-password")
        timeSource += 3_540.seconds

        val requests = List(5) {
            async { manager.getValidSession() }
        }

        refreshStarted.await()
        finishRefresh.complete(Unit)

        requests.awaitAll().forEach { session ->
            assertSame(refreshedSession, session)
        }

        assertEquals(
            listOf(initialSession.refreshToken),
            api.requestedRefreshTokens,
        )
        assertSame(refreshedSession, manager.session.value)
    }

    @Test
    fun `signing out during refresh does not restore session`() = runTest {
        val api = FakeAuthApi()
        val timeSource = TestTimeSource()
        val manager = FirebaseSessionManager(api, timeSource)
        val refreshStarted = CompletableDeferred<Unit>()
        val finishRefresh = CompletableDeferred<Unit>()

        api.refreshHandler = {
            refreshStarted.complete(Unit)
            finishRefresh.await()
            refreshedSession
        }

        manager.signIn("owner@example.com", "test-password")
        timeSource += 3_540.seconds

        val request = async { manager.getValidSession() }

        refreshStarted.await()
        manager.signOut()
        finishRefresh.complete(Unit)

        assertFailsWith<CancellationException> {
            request.await()
        }

        assertNull(manager.session.value)
    }

    @Test
    fun `invalid refresh token clears session`() = runTest {
        val api = FakeAuthApi()
        val timeSource = TestTimeSource()
        val manager = FirebaseSessionManager(api, timeSource)

        api.refreshHandler = {
            throw FirebaseAuthException(
                statusCode = 400,
                requiresSignIn = true,
            )
        }

        manager.signIn("owner@example.com", "test-password")
        timeSource += 3_540.seconds

        assertFailsWith<FirebaseAuthException> {
            manager.getValidSession()
        }

        assertNull(manager.session.value)
    }

    @Test
    fun `temporary refresh failure preserves session for retry`() = runTest {
        val api = FakeAuthApi()
        val timeSource = TestTimeSource()
        val manager = FirebaseSessionManager(api, timeSource)

        api.refreshHandler = {
            throw FirebaseAuthException(
                statusCode = 503,
                requiresSignIn = false,
            )
        }

        manager.signIn("owner@example.com", "test-password")
        timeSource += 3_540.seconds

        assertFailsWith<FirebaseAuthException> {
            manager.getValidSession()
        }

        assertSame(initialSession, manager.session.value)

        api.refreshHandler = { refreshedSession }

        assertSame(refreshedSession, manager.getValidSession())
        assertEquals(2, api.requestedRefreshTokens.size)
    }

    private inner class FakeAuthApi : FirebaseAuthApi {
        val requestedRefreshTokens = mutableListOf<String>()

        var refreshHandler: suspend () -> FirebaseSession = {
            refreshedSession
        }

        override suspend fun signIn(email: String, password: String): FirebaseSession = initialSession

        override suspend fun refreshSession(refreshToken: String): FirebaseSession {
            requestedRefreshTokens += refreshToken
            return refreshHandler()
        }
    }
}
