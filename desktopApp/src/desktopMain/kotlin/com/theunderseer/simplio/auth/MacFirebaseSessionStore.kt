package com.theunderseer.simplio.auth

import com.theunderseer.simplio.shared.firebase.auth.FirebaseSessionStorageException
import com.theunderseer.simplio.shared.firebase.auth.FirebaseSessionStorageOperation
import com.theunderseer.simplio.shared.firebase.auth.FirebaseSessionStore
import com.theunderseer.simplio.shared.firebase.auth.RememberedFirebaseSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path

internal class MacFirebaseSessionStore private constructor(private val projectId: String) : FirebaseSessionStore {
    override suspend fun read(allowUserInteraction: Boolean): RememberedFirebaseSession? = withContext(Dispatchers.IO) {
        val bytes = keychain(FirebaseSessionStorageOperation.Read) {
            readItemWithInteraction(projectId, allowUserInteraction)
        } ?: return@withContext null
        val parts = bytes.decodeToString().split('\n', limit = 2)
        if (parts.size != 2 || parts.any { it.isBlank() }) {
            keychain(FirebaseSessionStorageOperation.Clear) { clearItem(projectId, allowUserInteraction) }
            return@withContext null
        }
        RememberedFirebaseSession(parts[0], parts[1])
    }

    override suspend fun write(session: RememberedFirebaseSession, allowUserInteraction: Boolean) {
        withContext(Dispatchers.IO) {
            check('\n' !in session.userId)
            val data = (session.userId + "\n" + session.refreshToken).encodeToByteArray()
            keychain(FirebaseSessionStorageOperation.Write) { writeItem(projectId, data, allowUserInteraction) }
        }
    }

    override suspend fun clear(allowUserInteraction: Boolean) {
        withContext(Dispatchers.IO) {
            keychain(FirebaseSessionStorageOperation.Clear) { clearItem(projectId, allowUserInteraction) }
        }
    }

    private inline fun <T> keychain(operation: FirebaseSessionStorageOperation, action: () -> T): T = try {
        action()
    } catch (error: MacKeychainOperationException) {
        throw FirebaseSessionStorageException(
            cause = error,
            operation = operation,
            status = error.status,
            isTemporarilyUnavailable = error.isTemporarilyUnavailable,
        )
    } catch (error: IllegalStateException) {
        throw FirebaseSessionStorageException(cause = error, operation = operation)
    } catch (error: LinkageError) {
        throw FirebaseSessionStorageException(cause = error, operation = operation)
    }

    private external fun readItemWithInteraction(projectId: String, allowUserInteraction: Boolean): ByteArray?
    private external fun writeItem(projectId: String, data: ByteArray, allowUserInteraction: Boolean)
    private external fun clearItem(projectId: String, allowUserInteraction: Boolean)

    companion object {
        fun open(projectId: String): MacFirebaseSessionStore? {
            if (!System.getProperty("os.name").startsWith("Mac")) return null
            val resources = System.getProperty("compose.application.resources.dir") ?: return null
            val library = Path.of(resources).resolve("libSimplioNotifications.dylib").toAbsolutePath()
            if (!Files.isRegularFile(library)) return null
            return try {
                System.load(library.toString())
                MacFirebaseSessionStore(projectId)
            } catch (_: LinkageError) {
                null
            }
        }
    }
}

private class MacKeychainOperationException(val status: Int, val isTemporarilyUnavailable: Boolean) :
    IllegalStateException("Keychain OSStatus $status")
