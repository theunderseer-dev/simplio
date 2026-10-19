package com.theunderseer.simplio.feature.attendance.data.local.seed

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.entity.PackageTypeEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PackageTypeSeederTest {
    @Test
    fun `initialization seeds default package types into empty database`() = runTest {
        // Given
        val database = createDatabase()

        try {
            val seeder = createSeeder(database)
            // When
            seeder.seedDefaults()

            // Then
            val packageTypes =
                database
                    .packageTypeDao()
                    .getAll()

            assertEquals(
                expectedPackageTypes,
                packageTypes.map { it.copy(id = 0) },
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun `initialization does not duplicate default package types`() = runTest {
        // Given
        val database = createDatabase()

        try {
            val seeder = createSeeder(database)
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
        val database = createDatabase()

        try {
            val packageTypeDao = database.packageTypeDao()

            expectedPackageTypes
                .filter { it.maxVisitsPerAttendance == 1 }
                .forEach { packageType ->
                    packageTypeDao.insert(packageType)
                }

            val existingPackageTypes = packageTypeDao.getAll()
            val existingIds = existingPackageTypes.map { it.id }.toSet()
            val seeder = createSeeder(database)

            // When
            seeder.seedDefaults()

            // Then: existing package terms and identities are preserved
            val packageTypes = packageTypeDao.getAll()

            assertEquals(
                existingPackageTypes,
                packageTypes.filter { it.id in existingIds },
            )

            // And: the special paired package becomes available
            val duoPackage =
                assertNotNull(
                    packageTypes.singleOrNull {
                        it.name == "Duo 8 · Special rate"
                    },
                )

            assertEquals(
                expectedPackageTypes
                    .single { it.name == "Duo 8 · Special rate" }
                    .copy(id = duoPackage.id),
                duoPackage,
            )

            assertEquals(3, packageTypes.size)
        } finally {
            database.close()
        }
    }

    private val expectedPackageTypes = listOf(
        PackageTypeEntity(
            name = "4-class package",
            totalClasses = 4,
            validityWeeks = 4,
            price = 1400,
            maxVisitsPerAttendance = 1,
        ),
        PackageTypeEntity(
            name = "5-class package",
            totalClasses = 5,
            validityWeeks = 5,
            price = 1700,
            maxVisitsPerAttendance = 1,
        ),
        PackageTypeEntity(
            name = "Duo 8 · Special rate",
            totalClasses = 8,
            validityWeeks = 4,
            price = 2400,
            maxVisitsPerAttendance = 2,
        ),
    )

    private fun createDatabase(): AttendanceDatabase = Room
        .inMemoryDatabaseBuilder<AttendanceDatabase>()
        .setDriver(BundledSQLiteDriver())
        .build()

    private fun createSeeder(database: AttendanceDatabase): PackageTypeSeeder = PackageTypeSeeder(database)
}
