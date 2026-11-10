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
    fun `a rejected sign-in validation never exposes an authenticated session`() = runTest {
        val manager = FirebaseSessionManager(FakeAuthApi())

        assertFailsWith<IllegalStateException> {
            manager.signIn("owner@example.com", "test-password") { candidate ->
                assertSame(initialSession, candidate)
                assertNull(manager.session.value)
                error("Email verification required")
            }
        }
        assertNull(manager.session.value)
    }

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

    @Test
    fun `opted in sign-in can be restored after a restart and sign-out removes it`() = runTest {
        val api = FakeAuthApi()
        val store = FakeSessionStore()
        val manager = FirebaseSessionManager(api, sessionStore = store)
        manager.signIn("owner@example.com", "test-password", rememberSession = true)
        assertEquals(initialSession.refreshToken, store.saved?.refreshToken)

        val restarted = FirebaseSessionManager(api, sessionStore = store)
        assertSame(
            refreshedSession,
            restarted.restoreSession { candidate ->
                assertSame(refreshedSession, candidate)
                assertNull(restarted.session.value)
            },
        )
        assertEquals(listOf(initialSession.refreshToken), api.requestedRefreshTokens)
        assertEquals(refreshedSession.refreshToken, store.saved?.refreshToken)

        restarted.signOut()
        assertNull(restarted.session.value)
        assertNull(FirebaseSessionManager(api, sessionStore = store).restoreSession())
    }

    @Test
    fun `sign-in without remember me clears an older saved account`() = runTest {
        val store = FakeSessionStore()
        store.saved = RememberedFirebaseSession("other-user", "old-token")
        val manager = FirebaseSessionManager(FakeAuthApi(), sessionStore = store)
        manager.signIn("owner@example.com", "test-password")

        assertSame(initialSession, manager.session.value)
        assertNull(store.saved)
    }

    @Test
    fun `temporary restore failure retains saved session while invalid credentials remove it`() = runTest {
        val api = FakeAuthApi()
        val store = FakeSessionStore()
        store.saved = RememberedFirebaseSession(initialSession.userId, initialSession.refreshToken)
        val manager = FirebaseSessionManager(api, sessionStore = store)
        api.refreshHandler = { throw FirebaseAuthException(503, requiresSignIn = false) }
        assertFailsWith<FirebaseAuthException> { manager.restoreSession() }
        assertEquals(initialSession.refreshToken, store.saved?.refreshToken)
        assertNull(manager.session.value)

        api.refreshHandler = { throw FirebaseAuthException(400, requiresSignIn = true) }
        assertNull(manager.restoreSession())
        assertNull(store.saved)
    }

    @Test
    fun `restore rejects a refresh response for a different account`() = runTest {
        val store = FakeSessionStore()
        store.saved = RememberedFirebaseSession("other-user", initialSession.refreshToken)
        val manager = FirebaseSessionManager(FakeAuthApi(), sessionStore = store)

        assertNull(manager.restoreSession())
        assertNull(manager.session.value)
        assertNull(store.saved)
    }

    @Test
    fun `sign-out during restore prevents both session activation and credential re-saving`() = runTest {
        val api = FakeAuthApi()
        val store = FakeSessionStore()
        store.saved = RememberedFirebaseSession(initialSession.userId, initialSession.refreshToken)
        val manager = FirebaseSessionManager(api, sessionStore = store)
        val refreshStarted = CompletableDeferred<Unit>()
        val finishRefresh = CompletableDeferred<Unit>()
        api.refreshHandler = {
            refreshStarted.complete(Unit)
            finishRefresh.await()
            refreshedSession
        }

        val restore = async { manager.restoreSession() }
        refreshStarted.await()
        manager.signOut()
        finishRefresh.complete(Unit)

        assertFailsWith<CancellationException> { restore.await() }
        assertNull(manager.session.value)
        assertNull(store.saved)
    }

    @Test
    fun `failed sign-out keeps the session visible until stored credentials can be cleared`() = runTest {
        val store = FakeSessionStore()
        val manager = FirebaseSessionManager(FakeAuthApi(), sessionStore = store)
        manager.signIn("owner@example.com", "test-password", rememberSession = true)
        store.failClear = true

        assertFailsWith<FirebaseSessionStorageException> { manager.signOut() }
        assertSame(initialSession, manager.session.value)
        assertEquals(initialSession.refreshToken, store.saved?.refreshToken)
    }

    private class FakeSessionStore : FirebaseSessionStore {
        var saved: RememberedFirebaseSession? = null
        var failClear = false

        override suspend fun read(): RememberedFirebaseSession? = saved

        override suspend fun write(session: RememberedFirebaseSession) {
            saved = session
        }

        override suspend fun clear() {
            if (failClear) throw FirebaseSessionStorageException()
            saved = null
        }
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
