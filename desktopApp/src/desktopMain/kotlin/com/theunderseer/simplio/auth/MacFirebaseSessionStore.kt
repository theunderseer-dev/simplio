package com.theunderseer.simplio.auth

import com.theunderseer.simplio.shared.firebase.auth.FirebaseSessionStorageException
import com.theunderseer.simplio.shared.firebase.auth.FirebaseSessionStore
import com.theunderseer.simplio.shared.firebase.auth.RememberedFirebaseSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path

internal class MacFirebaseSessionStore private constructor(private val projectId: String) : FirebaseSessionStore {
    override suspend fun read(): RememberedFirebaseSession? = withContext(Dispatchers.IO) {
        val bytes = keychain { readItem(projectId) } ?: return@withContext null
        val parts = bytes.decodeToString().split('\n', limit = 2)
        if (parts.size != 2 || parts.any { it.isBlank() }) {
            keychain { clearItem(projectId) }
            return@withContext null
        }
        RememberedFirebaseSession(parts[0], parts[1])
    }

    override suspend fun write(session: RememberedFirebaseSession) {
        withContext(Dispatchers.IO) {
            check('\n' !in session.userId)
            keychain { writeItem(projectId, (session.userId + "\n" + session.refreshToken).encodeToByteArray()) }
        }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            keychain { clearItem(projectId) }
        }
    }

    private inline fun <T> keychain(action: () -> T): T = try {
        action()
    } catch (error: IllegalStateException) {
        throw FirebaseSessionStorageException(error)
    } catch (error: LinkageError) {
        throw FirebaseSessionStorageException(error)
    }

    private external fun readItem(projectId: String): ByteArray?
    private external fun writeItem(projectId: String, data: ByteArray)
    private external fun clearItem(projectId: String)

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
