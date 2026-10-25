package com.theunderseer.simplio.feature.attendance.data.local.repository

import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomSyncOutbox
import com.theunderseer.simplio.feature.attendance.domain.model.CreatePackageTypeResult
import com.theunderseer.simplio.feature.attendance.domain.model.PackageTypeValidation
import com.theunderseer.simplio.feature.attendance.domain.usecase.CreatePackageTypeUseCase
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.seedSyncDefaults
import com.theunderseer.simplio.feature.attendance.fixtures.testCreatePackageTypeRequest
import com.theunderseer.simplio.feature.attendance.fixtures.withSyncDatabases
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class PackageTypeCreationTest {
    @Test
    fun `custom unlimited and finite catalogs are persisted and queued once`() = runTest {
        withSyncDatabases { database, _ ->
            database.seedSyncDefaults()
            val useCase = CreatePackageTypeUseCase(RoomPackageTypeCreationRepository(database))
            AttendanceTestData.privatePackages.forEach { type ->
                val created = assertIs<CreatePackageTypeResult.Success>(useCase(testCreatePackageTypeRequest(type)))
                assertEquals(type.copy(id = created.packageType.id), created.packageType)
                assertNull(database.packageTypeDao().getById(created.packageType.id)?.validityWeeks)
            }
            val finite = assertIs<CreatePackageTypeResult.Success>(
                useCase(testCreatePackageTypeRequest().copy(name = "Finite private", validityWeeks = 6)),
            ).packageType
            assertEquals(6, database.packageTypeDao().getById(finite.id)?.validityWeeks)
            assertEquals(8, database.packageTypeDao().getAll().size)
        }
    }

    @Test
    fun `duplicate name and invalid inputs leave catalog and queue untouched`() = runTest {
        withSyncDatabases { database, _ ->
            database.seedSyncDefaults()
            val repository = RoomPackageTypeCreationRepository(database)
            val request = testCreatePackageTypeRequest()
            repository.create(request)
            val before = database.packageTypeDao().getAll()
            val pending = RoomSyncOutbox(database).getPending()
            assertEquals(
                CreatePackageTypeResult.DuplicateName,
                repository.create(request.copy(name = "  " + request.name.uppercase().replace(" ", "  ") + "  ")),
            )
            listOf(
                request.copy(name = " ") to PackageTypeValidation.NAME,
                request.copy(totalClasses = 0) to PackageTypeValidation.CLASSES,
                request.copy(price = -1) to PackageTypeValidation.PRICE,
                request.copy(validityWeeks = 0) to PackageTypeValidation.VALIDITY,
                request.copy(validityWeeks = Int.MAX_VALUE) to PackageTypeValidation.VALIDITY,
                request.copy(maxVisitsPerAttendance = 3) to PackageTypeValidation.VISITS,
                request.copy(totalClasses = 1, maxVisitsPerAttendance = 2) to PackageTypeValidation.VISITS,
            ).forEach { (invalid, reason) ->
                assertEquals(CreatePackageTypeResult.Invalid(reason), repository.create(invalid))
            }
            assertEquals(before, database.packageTypeDao().getAll())
            assertEquals(pending, RoomSyncOutbox(database).getPending())
        }
    }

    @Test
    fun `concurrent duplicate saves create one catalog entry`() = runTest {
        withSyncDatabases { database, _ ->
            val repository = RoomPackageTypeCreationRepository(database)
            val request = testCreatePackageTypeRequest()
            val first = async { repository.create(request) }
            val second = async { repository.create(request.copy(name = request.name.uppercase())) }
            val results = listOf(first.await(), second.await())
            assertEquals(1, results.count { it is CreatePackageTypeResult.Success })
            assertEquals(1, results.count { it == CreatePackageTypeResult.DuplicateName })
            assertEquals(1, database.packageTypeDao().getAll().size)
        }
    }
}
