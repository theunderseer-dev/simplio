package com.theunderseer.simplio.feature.attendance.data.local.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.domain.model.PackagePlan
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.fourClassPackage
import com.theunderseer.simplio.feature.attendance.fixtures.testClassPackage
import com.theunderseer.simplio.feature.attendance.fixtures.testPackageTypeEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RoomPackageTypeRepositoryTest {
    private val fiveClassPackage = testClassPackage(
        plan = PackagePlan.FIVE_CLASSES,
    ).copy(validityWeeks = 6)

    private lateinit var database: AttendanceDatabase
    private lateinit var repository: RoomPackageTypeRepository

    @BeforeTest
    fun setUp() {
        database = Room
            .inMemoryDatabaseBuilder<AttendanceDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()

        repository = RoomPackageTypeRepository(
            packageTypeDao = database.packageTypeDao(),
        )
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun `getting all package types returns persisted package types`() = runTest {
        // Given
        val firstPackage = persistPackageType(
            fourClassPackage.copy(validityWeeks = 5),
        )
        val secondPackage = persistPackageType(fiveClassPackage)

        // When
        val packageTypes = repository.getAll()

        // Then
        assertEquals(
            listOf(firstPackage, secondPackage),
            packageTypes,
        )
    }

    @Test
    fun `getting package type by id returns persisted package type`() = runTest {
        // Given
        persistPackageType(fourClassPackage)
        val expected = persistPackageType(fiveClassPackage)

        // When
        val packageType = repository.getById(expected.id)

        // Then
        assertEquals(expected, packageType)
    }

    @Test
    fun `getting package type by missing id returns null`() = runTest {
        // Given
        persistPackageType(fourClassPackage)

        // When
        val packageType = repository.getById(
            id = 999,
        )

        // Then
        assertNull(packageType)
    }

    private suspend fun persistPackageType(packageType: PackageType): PackageType {
        val entity = testPackageTypeEntity(
            packageType = packageType,
        ).copy(id = 0)

        val id = database.packageTypeDao().insert(entity).toInt()

        return packageType.copy(id = id)
    }
}
