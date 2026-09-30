package com.theunderseer.simplio.feature.attendance.data.local.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.entity.PackageTypeEntity
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RoomPackageTypeRepositoryTest {
    private lateinit var database: AttendanceDatabase
    private lateinit var repository: RoomPackageTypeRepository

    @BeforeTest
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder<AttendanceDatabase>()
                .setDriver(BundledSQLiteDriver())
                .build()

        repository =
            RoomPackageTypeRepository(
                packageTypeDao = database.packageTypeDao(),
            )
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun `getting all package types returns persisted package types`() =
        runTest {
            // Given
            val firstId =
                database
                    .packageTypeDao()
                    .insert(
                        PackageTypeEntity(
                            name = "4-class package",
                            totalClasses = 4,
                            validityWeeks = 5,
                            price = 1400,
                        ),
                    ).toInt()

            val secondId =
                database
                    .packageTypeDao()
                    .insert(
                        PackageTypeEntity(
                            name = "5-class package",
                            totalClasses = 5,
                            validityWeeks = 6,
                            price = 1700,
                        ),
                    ).toInt()

            // When
            val packageTypes =
                repository.getAll()

            // Then
            assertEquals(
                listOf(
                    PackageType(
                        id = firstId,
                        name = "4-class package",
                        totalClasses = 4,
                        validityWeeks = 5,
                        price = 1400,
                    ),
                    PackageType(
                        id = secondId,
                        name = "5-class package",
                        totalClasses = 5,
                        validityWeeks = 6,
                        price = 1700,
                    ),
                ),
                packageTypes,
            )
        }

    @Test
    fun `getting package type by id returns persisted package type`() =
        runTest {
            // Given
            val packageTypeId =
                database
                    .packageTypeDao()
                    .insert(
                        PackageTypeEntity(
                            name = "5-class package",
                            totalClasses = 5,
                            validityWeeks = 6,
                            price = 1700,
                        ),
                    ).toInt()

            // When
            val packageType =
                repository.getById(packageTypeId)

            // Then
            assertEquals(
                PackageType(
                    id = packageTypeId,
                    name = "5-class package",
                    totalClasses = 5,
                    validityWeeks = 6,
                    price = 1700,
                ),
                packageType,
            )
        }

    @Test
    fun `getting package type by missing id returns null`() =
        runTest {
            // When
            val packageType =
                repository.getById(
                    id = 999,
                )

            // Then
            assertNull(packageType)
        }
}
