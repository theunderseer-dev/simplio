package com.theunderseer.simplio.feature.attendance.data.local.seed

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.entity.PackageTypeEntity
import com.theunderseer.simplio.feature.attendance.data.local.transaction.RoomTransactionRunner
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PackageTypeSeederTest {
    @Test
    fun `initialization seeds default package types into empty database`() = runTest {
        // Given
        val database =
            Room
                .inMemoryDatabaseBuilder<AttendanceDatabase>()
                .setDriver(BundledSQLiteDriver())
                .build()

        try {
            val seeder =
                PackageTypeSeeder(
                    packageTypeDao = database.packageTypeDao(),
                    transactionRunner =
                    RoomTransactionRunner(
                        database = database,
                    ),
                )

            // When
            seeder.seedDefaults()

            // Then
            val packageTypes =
                database
                    .packageTypeDao()
                    .getAll()

            assertEquals(
                2,
                packageTypes.size,
            )

            assertEquals(
                listOf(
                    PackageTypeEntity(
                        id = packageTypes[0].id,
                        name = "4-class package",
                        totalClasses = 4,
                        validityWeeks = 5,
                        price = 1400,
                    ),
                    PackageTypeEntity(
                        id = packageTypes[1].id,
                        name = "5-class package",
                        totalClasses = 5,
                        validityWeeks = 6,
                        price = 1700,
                    ),
                ),
                packageTypes,
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun `initialization does not duplicate default package types`() = runTest {
        // Given
        val database =
            Room
                .inMemoryDatabaseBuilder<AttendanceDatabase>()
                .setDriver(BundledSQLiteDriver())
                .build()

        try {
            val seeder =
                PackageTypeSeeder(
                    packageTypeDao = database.packageTypeDao(),
                    transactionRunner =
                    RoomTransactionRunner(
                        database = database,
                    ),
                )

            seeder.seedDefaults()

            // When
            seeder.seedDefaults()

            // Then
            assertEquals(
                2,
                database
                    .packageTypeDao()
                    .getAll()
                    .size,
            )
        } finally {
            database.close()
        }
    }
}
