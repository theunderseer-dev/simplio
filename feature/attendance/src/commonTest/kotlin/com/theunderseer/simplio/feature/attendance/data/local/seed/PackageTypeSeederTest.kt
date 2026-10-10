package com.theunderseer.simplio.feature.attendance.data.local.seed

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.entity.PackageTypeEntity
import com.theunderseer.simplio.feature.attendance.data.local.transaction.RoomTransactionRunner
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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
                3,
                packageTypes.size,
            )

            assertEquals(
                listOf(
                    PackageTypeEntity(
                        id = packageTypes[0].id,
                        name = "4-class package",
                        totalClasses = 4,
                        validityWeeks = 4,
                        price = 1400,
                    ),
                    PackageTypeEntity(
                        id = packageTypes[1].id,
                        name = "5-class package",
                        totalClasses = 5,
                        validityWeeks = 5,
                        price = 1700,
                    ),
                    PackageTypeEntity(
                        id = packageTypes[2].id,
                        name = "Duo 8 · Special rate",
                        totalClasses = 8,
                        validityWeeks = 4,
                        price = 2400,
                        maxVisitsPerAttendance = 2,
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
            val initialPackageTypes = database.packageTypeDao().getAll()

            // When
            seeder.seedDefaults()

            // Then
            assertEquals(
                initialPackageTypes,
                database.packageTypeDao().getAll(),
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun `initialization adds special duo package to existing catalog`() = runTest {
        // Given
        val database =
            Room
                .inMemoryDatabaseBuilder<AttendanceDatabase>()
                .setDriver(BundledSQLiteDriver())
                .build()

        try {
            val packageTypeDao = database.packageTypeDao()

            packageTypeDao.insert(
                PackageTypeEntity(
                    name = "4-class package",
                    totalClasses = 4,
                    validityWeeks = 4,
                    price = 1400,
                ),
            )

            packageTypeDao.insert(
                PackageTypeEntity(
                    name = "5-class package",
                    totalClasses = 5,
                    validityWeeks = 5,
                    price = 1700,
                ),
            )

            val existingPackageTypes = packageTypeDao.getAll()
            val existingIds = existingPackageTypes.map { it.id }.toSet()

            val seeder =
                PackageTypeSeeder(
                    packageTypeDao = packageTypeDao,
                    transactionRunner =
                    RoomTransactionRunner(
                        database = database,
                    ),
                )

            // When
            seeder.seedDefaults()

            // Then: existing package terms and identities are preserved
            val packageTypes = packageTypeDao.getAll()

            assertEquals(
                existingPackageTypes,
                packageTypes.filter { it.id in existingIds },
            )

            // And: the special paired package becomes available
            val pairedPackage =
                assertNotNull(
                    packageTypes.singleOrNull {
                        it.name == "Duo 8 · Special rate"
                    },
                )

            assertEquals(
                PackageTypeEntity(
                    id = pairedPackage.id,
                    name = "Duo 8 · Special rate",
                    totalClasses = 8,
                    validityWeeks = 4,
                    price = 2400,
                    maxVisitsPerAttendance = 2,
                ),
                pairedPackage,
            )

            assertEquals(3, packageTypes.size)
        } finally {
            database.close()
        }
    }
}
