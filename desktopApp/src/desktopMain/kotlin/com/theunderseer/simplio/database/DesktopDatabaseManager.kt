package com.theunderseer.simplio.database

import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.HexFormat

internal sealed interface AccountDatabaseSelection {
    data class Ready(val path: Path) : AccountDatabaseSelection

    data object LegacyDataAvailable : AccountDatabaseSelection
}

internal class DesktopDatabaseManager private constructor(
    private val directory: Path,
    private val channel: FileChannel,
    private val lock: FileLock,
) : AutoCloseable {
    private val legacyPath = directory.resolve("simplio.db")
    private val ownerPath = directory.resolve("legacy-owner")
    private var isClosed = false

    @Synchronized
    fun selectAccount(userId: String): AccountDatabaseSelection {
        checkOpen()
        val accountKey = accountKey(userId)
        val legacyOwner = readLegacyOwner()

        if (legacyOwner == accountKey) {
            return AccountDatabaseSelection.Ready(legacyPath)
        }

        val path = accountPath(accountKey)

        if (legacyOwner == null && Files.isRegularFile(legacyPath) && !Files.exists(path.parent)) {
            return AccountDatabaseSelection.LegacyDataAvailable
        }

        return AccountDatabaseSelection.Ready(prepare(path))
    }

    @Synchronized
    fun useExistingData(userId: String): Path {
        checkOpen()
        val accountKey = accountKey(userId)
        val owner = readLegacyOwner()

        check(owner == null || owner == accountKey) {
            "Existing data already belongs to another account"
        }

        if (owner == accountKey) {
            return legacyPath
        }

        check(Files.isRegularFile(legacyPath)) { "Existing database was not found" }
        check(!Files.exists(accountPath(accountKey).parent)) {
            "This account already has its own database"
        }

        writeLegacyOwner(accountKey)
        return legacyPath
    }

    @Synchronized
    fun useNewDatabase(userId: String): Path {
        checkOpen()
        val accountKey = accountKey(userId)
        check(readLegacyOwner() != accountKey) {
            "This account already uses the existing database"
        }

        return prepare(accountPath(accountKey))
    }

    @Synchronized
    fun localDatabase(): Path {
        checkOpen()
        val path = if (readLegacyOwner() == null) {
            legacyPath
        } else {
            directory.resolve("local").resolve("simplio.db")
        }

        return prepare(path)
    }

    @Synchronized
    override fun close() {
        if (!isClosed) {
            isClosed = true

            try {
                lock.release()
            } finally {
                channel.close()
            }
        }
    }

    private fun accountPath(accountKey: String): Path = directory
        .resolve("accounts")
        .resolve(accountKey)
        .resolve("simplio.db")

    private fun accountKey(userId: String): String {
        require(userId.isNotBlank()) { "Account ID must not be blank" }
        val bytes = MessageDigest.getInstance("SHA-256").digest(userId.toByteArray(Charsets.UTF_8))
        return HexFormat.of().formatHex(bytes)
    }

    private fun prepare(path: Path): Path {
        Files.createDirectories(path.parent)
        return path
    }

    private fun readLegacyOwner(): String? {
        if (!Files.exists(ownerPath)) {
            return null
        }

        val owner = Files.readString(ownerPath).trim()
        check(owner.length == 64 && owner.all { it in '0'..'9' || it in 'a'..'f' }) {
            "The database ownership file is invalid. Restore it before opening local data."
        }

        return owner
    }

    private fun writeLegacyOwner(accountKey: String) {
        val temporaryPath = Files.createTempFile(directory, "legacy-owner-", ".tmp")

        try {
            Files.writeString(temporaryPath, accountKey)
            FileChannel.open(temporaryPath, StandardOpenOption.WRITE).use { it.force(true) }
            Files.move(temporaryPath, ownerPath, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(temporaryPath)
        }
    }

    private fun checkOpen() {
        check(!isClosed) { "Database manager is closed" }
    }

    companion object {
        fun open(userHome: Path = Path.of(System.getProperty("user.home"))): DesktopDatabaseManager {
            val directory = simplioDatabasePath(userHome).parent
            Files.createDirectories(directory)
            val channel = FileChannel.open(
                directory.resolve("database.lock"),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
            )

            try {
                val lock = try {
                    channel.tryLock()
                } catch (_: OverlappingFileLockException) {
                    null
                }

                checkNotNull(lock) { "Simplio is already running. Close the other instance first." }
                return DesktopDatabaseManager(directory, channel, lock)
            } catch (error: Exception) {
                channel.close()
                throw error
            }
        }
    }
}
