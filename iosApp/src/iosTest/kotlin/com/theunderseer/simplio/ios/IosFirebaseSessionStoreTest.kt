package com.theunderseer.simplio.ios

import com.theunderseer.simplio.shared.firebase.auth.FirebaseAuthApi
import com.theunderseer.simplio.shared.firebase.auth.FirebaseSession
import com.theunderseer.simplio.shared.firebase.auth.FirebaseSessionManager
import com.theunderseer.simplio.shared.firebase.auth.FirebaseSessionStorageException
import com.theunderseer.simplio.shared.firebase.auth.FirebaseSessionStorageOperation
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSUUID
import platform.Security.errSecInteractionNotAllowed
import platform.Security.errSecMissingEntitlement
import platform.Security.errSecNotAvailable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class IosFirebaseSessionStoreTest {
    @Test
    fun keychainAvailabilityFailuresCarryTheOperationAndRawStatus() {
        for (status in listOf(errSecInteractionNotAllowed, errSecNotAvailable)) {
            val error = assertFailsWith<FirebaseSessionStorageException> {
                requireIosKeychainSuccess(status, FirebaseSessionStorageOperation.Read)
            }
            assertEquals(FirebaseSessionStorageOperation.Read, error.operation)
            assertEquals(status, error.status)
            assertTrue(error.isTemporarilyUnavailable)
        }
    }

    @Test
    fun missingEntitlementIsNotATemporaryKeychainFailure() {
        val error = assertFailsWith<FirebaseSessionStorageException> {
            requireIosKeychainSuccess(errSecMissingEntitlement, FirebaseSessionStorageOperation.Write)
        }
        assertEquals(FirebaseSessionStorageOperation.Write, error.operation)
        assertEquals(errSecMissingEntitlement, error.status)
        assertFalse(error.isTemporarilyUnavailable)
    }

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
