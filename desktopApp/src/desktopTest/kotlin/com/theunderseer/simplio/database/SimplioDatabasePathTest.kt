package com.theunderseer.simplio.database

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimplioDatabasePathTest {
    @Test
    fun `database path is located in macOS application support directory`() {
        // Given
        val userHome = Path.of("/Users/eugene")

        // When
        val path =
            simplioDatabasePath(
                userHome = userHome,
            )

        // Then
        assertEquals(
            Path.of(
                "/Users/eugene",
                "Library",
                "Application Support",
                "Simplio",
                "simplio.db",
            ),
            path,
        )
    }

    @Test
    fun `preparing database path creates application directory`() {
        // Given
        val userHome =
            Files.createTempDirectory(
                "simplio-test-home",
            )

        try {
            // When
            val path =
                prepareSimplioDatabasePath(
                    userHome = userHome,
                )

            // Then
            assertEquals(
                userHome
                    .resolve("Library")
                    .resolve("Application Support")
                    .resolve("Simplio")
                    .resolve("simplio.db"),
                path,
            )

            assertTrue(
                Files.isDirectory(
                    path.parent,
                ),
            )
        } finally {
            userHome
                .toFile()
                .deleteRecursively()
        }
    }
}
