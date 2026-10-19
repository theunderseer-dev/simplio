package com.theunderseer.simplio.database

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DesktopDatabaseManagerTest {
    private lateinit var userHome: Path
    private lateinit var databases: DesktopDatabaseManager

    @BeforeTest
    fun setUp() {
        userHome = Files.createTempDirectory("simplio-accounts-")
        databases = DesktopDatabaseManager.open(userHome)
    }

    @AfterTest
    fun tearDown() {
        databases.close()
        userHome.toFile().deleteRecursively()
    }

    @Test
    fun `different accounts have different stable database paths after reopening`() {
        val first = readyPath("account-a")
        val second = readyPath("account-b")

        assertNotEquals(first, second)
        assertTrue(Files.isDirectory(first.parent))
        assertFalse(Files.exists(first))

        databases.close()
        databases = DesktopDatabaseManager.open(userHome)

        assertEquals(first, readyPath("account-a"))
        assertEquals(second, readyPath("account-b"))
    }

    @Test
    fun `existing data requires a choice and is not assigned by signing in`() {
        val legacy = createLegacyDatabase()

        assertEquals(AccountDatabaseSelection.LegacyDataAvailable, databases.selectAccount("account-a"))
        assertEquals(AccountDatabaseSelection.LegacyDataAvailable, databases.selectAccount("account-b"))
        assertEquals("existing data", Files.readString(legacy))
        assertFalse(Files.exists(legacy.parent.resolve("legacy-owner")))
    }

    @Test
    fun `using existing data preserves database and sidecar files and assigns one owner`() {
        val legacy = createLegacyDatabase()
        val wal = legacy.resolveSibling("simplio.db-wal")
        Files.writeString(wal, "pending WAL data")

        assertEquals(legacy, databases.useExistingData("account-a"))
        assertEquals(legacy, databases.useExistingData("account-a"))
        assertEquals("existing data", Files.readString(legacy))
        assertEquals("pending WAL data", Files.readString(wal))
        assertNotEquals(legacy, readyPath("account-b"))
        assertFailsWith<IllegalStateException> { databases.useExistingData("account-b") }

        databases.close()
        databases = DesktopDatabaseManager.open(userHome)

        assertEquals(legacy, readyPath("account-a"))
        assertNotEquals(legacy, readyPath("account-b"))
    }

    @Test
    fun `starting empty preserves existing data and remembers the choice`() {
        val legacy = createLegacyDatabase()
        val path = databases.useNewDatabase("account-a")

        assertNotEquals(legacy, path)
        assertEquals(path, readyPath("account-a"))
        assertEquals("existing data", Files.readString(legacy))
        assertFailsWith<IllegalStateException> { databases.useExistingData("account-a") }
        assertEquals(AccountDatabaseSelection.LegacyDataAvailable, databases.selectAccount("account-b"))

        databases.close()
        databases = DesktopDatabaseManager.open(userHome)

        assertEquals(path, readyPath("account-a"))
    }

    @Test
    fun `local launch cannot open data assigned to an account`() {
        val legacy = createLegacyDatabase()
        assertEquals(legacy, databases.localDatabase())

        databases.useExistingData("account-a")

        val local = databases.localDatabase()
        assertNotEquals(legacy, local)
        assertNotEquals(readyPath("account-b"), local)
        assertEquals(legacy.parent.resolve("local").resolve("simplio.db"), local)
    }

    @Test
    fun `account IDs cannot escape their database directory`() {
        val path = readyPath("../../outside/account")
        val accountsDirectory = simplioDatabasePath(userHome).parent.resolve("accounts")

        assertTrue(path.startsWith(accountsDirectory))
        assertEquals(accountsDirectory, path.parent.parent)
        assertEquals(64, path.parent.fileName.toString().length)
    }

    @Test
    fun `blank account ID is rejected without claiming existing data`() {
        val legacy = createLegacyDatabase()

        assertFailsWith<IllegalArgumentException> { databases.selectAccount(" ") }
        assertFailsWith<IllegalArgumentException> { databases.useExistingData("") }
        assertFailsWith<IllegalArgumentException> { databases.useNewDatabase("\n") }
        assertFalse(Files.exists(legacy.parent.resolve("legacy-owner")))
    }

    @Test
    fun `invalid ownership file fails without exposing existing data`() {
        val legacy = createLegacyDatabase()
        Files.writeString(legacy.parent.resolve("legacy-owner"), "damaged")

        assertFailsWith<IllegalStateException> { databases.selectAccount("account-b") }
        assertFailsWith<IllegalStateException> { databases.useExistingData("account-b") }
        assertFailsWith<IllegalStateException> { databases.useNewDatabase("account-b") }
        assertFailsWith<IllegalStateException> { databases.localDatabase() }
        assertEquals("existing data", Files.readString(legacy))
    }

    @Test
    fun `assigned account cannot accidentally switch to an empty database`() {
        val legacy = createLegacyDatabase()
        databases.useExistingData("account-a")

        assertFailsWith<IllegalStateException> { databases.useNewDatabase("account-a") }
        assertEquals(legacy, readyPath("account-a"))
    }

    @Test
    fun `missing existing database cannot be assigned`() {
        assertFailsWith<IllegalStateException> { databases.useExistingData("account-a") }
        assertFalse(Files.exists(simplioDatabasePath(userHome).parent.resolve("legacy-owner")))
    }

    @Test
    fun `second application instance is rejected and closing releases its lock`() {
        assertFailsWith<IllegalStateException> { DesktopDatabaseManager.open(userHome) }

        databases.close()
        databases.close()

        databases = DesktopDatabaseManager.open(userHome)
        assertIs<AccountDatabaseSelection.Ready>(databases.selectAccount("account-a"))
    }

    @Test
    fun `closed manager cannot select or assign databases`() {
        createLegacyDatabase()
        databases.close()

        assertFailsWith<IllegalStateException> { databases.selectAccount("account-a") }
        assertFailsWith<IllegalStateException> { databases.useExistingData("account-a") }
        assertFailsWith<IllegalStateException> { databases.useNewDatabase("account-a") }
        assertFailsWith<IllegalStateException> { databases.localDatabase() }
    }

    private fun readyPath(userId: String): Path =
        assertIs<AccountDatabaseSelection.Ready>(databases.selectAccount(userId)).path

    private fun createLegacyDatabase(): Path = simplioDatabasePath(userHome).also {
        Files.writeString(it, "existing data")
    }
}
