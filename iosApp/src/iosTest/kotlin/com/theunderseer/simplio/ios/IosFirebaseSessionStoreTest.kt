package com.theunderseer.simplio.ios

import com.theunderseer.simplio.shared.firebase.auth.FirebaseAuthApi
import com.theunderseer.simplio.shared.firebase.auth.FirebaseSession
import com.theunderseer.simplio.shared.firebase.auth.FirebaseSessionManager
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class IosFirebaseSessionStoreTest {
    @Test
    fun rememberMeRestoresOnlyTheSameProjectAndSignOutClearsIt() = runTest {
        val projectId = "test-${NSUUID().UUIDString}"
        val store = IosFirebaseSessionStore(projectId)
        val initial = FirebaseSession("test-user", "initial-id", "initial-refresh", 3_600)
        val refreshed = FirebaseSession("test-user", "new-id", "new-refresh", 3_600)
        val auth = object : FirebaseAuthApi {
            override suspend fun signIn(email: String, password: String) = initial

            override suspend fun refreshSession(refreshToken: String): FirebaseSession {
                assertEquals(initial.refreshToken, refreshToken)
                return refreshed
            }
        }

        fun newManager(id: String = projectId): FirebaseSessionManager =
            FirebaseSessionManager(auth, sessionStore = IosFirebaseSessionStore(id))

        try {
            newManager().signIn("user@example.com", "fixture-password")
            assertNull(newManager().restoreSession())
            newManager().signIn("user@example.com", "fixture-password", rememberSession = true)
            assertNull(newManager("$projectId-other").restoreSession())

            val relaunched = newManager()
            val restored = relaunched.restoreSession { candidate ->
                assertEquals(initial.userId, candidate.userId)
                assertNull(relaunched.session.value)
            }
            assertSame(refreshed, restored)
            assertEquals(refreshed.refreshToken, store.read()?.refreshToken)

            relaunched.signOut()
            assertNull(relaunched.session.value)
            assertNull(newManager().restoreSession())
        } finally {
            store.clear(allowUserInteraction = false)
        }
    }
}
