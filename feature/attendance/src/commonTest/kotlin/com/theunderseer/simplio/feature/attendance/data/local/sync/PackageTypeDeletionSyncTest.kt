package com.theunderseer.simplio.feature.attendance.data.local.sync

import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomPackageTypeCreationRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomPackageTypeDeletionRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomPackageTypeRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.data.local.seed.PackageTypeSeeder
import com.theunderseer.simplio.feature.attendance.data.sync.UploadPreparationFailure
import com.theunderseer.simplio.feature.attendance.data.sync.UploadProblem
import com.theunderseer.simplio.feature.attendance.domain.model.CreatePackageTypeRequest
import com.theunderseer.simplio.feature.attendance.domain.model.CreatePackageTypeResult
import com.theunderseer.simplio.feature.attendance.fake.FakeAttendanceCloud
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.seedClassAttendanceStudents
import com.theunderseer.simplio.feature.attendance.fixtures.testUploadTarget
import com.theunderseer.simplio.feature.attendance.fixtures.withSyncDatabases
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PackageTypeDeletionSyncTest {
    @Test
    fun `removal hides a template and retains purchase terms and visits after sync`() = runTest {
        withSyncDatabases { source, device ->
            val registered = source.seedClassAttendanceStudents(activated = true).first()
            val original = registered.studentPackage
            val catalog = RoomPackageTypeRepository(source.packageTypeDao())
            val deletion = RoomPackageTypeDeletionRepository(source)
            val cloud = FakeAttendanceCloud()
            cloud.uploadPending(source)

            deletion.delete(original.packageType.id)
            deletion.delete(original.packageType.id)
            PackageTypeSeeder(source).seedDefaults()
            assertNull(catalog.getById(original.packageType.id))
            assertTrue(catalog.getAll().none { it.id == original.packageType.id })
            assertEquals(1, source.syncPullDao().pendingCount())
            val packages = RoomStudentPackageRepository(source)
            assertEquals(original, packages.getLatestPackageForStudent(registered.student.id))
            assertFailsWith<IllegalArgumentException> {
                packages.create(registered.student.id, original.packageType, AttendanceTestData.secondClassDate, null)
            }

            val replacement = assertIs<CreatePackageTypeResult.Success>(
                RoomPackageTypeCreationRepository(source).create(
                    original.packageType.let {
                        CreatePackageTypeRequest(it.name, it.totalClasses, it.price, it.validityWeeks, 1)
                    },
                ),
            ).packageType
            assertNotEquals(original.packageType.id, replacement.id)
            cloud.uploadPending(source)

            val snapshot = cloud.snapshot()
            val oldType = snapshot.documents.single { it.isArchivedPackageType() }
            val newType = snapshot.documents.single {
                it.type == SyncEntityType.PACKAGE_TYPE &&
                    !it.isArchivedPackageType() &&
                    it.string("name") == replacement.name
            }
            assertEquals("default-plan-four_classes", oldType.cloudId)
            assertNotEquals(oldType.cloudId, newType.cloudId)
            val pull = RoomPullStore(device, testUploadTarget)
            pull.apply(snapshot)
            val restoredCatalog = RoomPackageTypeRepository(device.packageTypeDao()).getAll()
            assertEquals(1, restoredCatalog.count { it.name == replacement.name })
            val student = device.studentDao().getAll().single { it.fullName == registered.student.fullName }
            val restored = checkNotNull(RoomStudentPackageRepository(device).getLatestPackageForStudent(student.id))
            assertEquals(original.packageType.validityWeeks, restored.packageType.validityWeeks)
            assertEquals(original.expiresOn, restored.expiresOn)
            assertEquals(original.attendanceHistory, restored.attendanceHistory)
            assertNull(RoomPackageTypeRepository(device.packageTypeDao()).getById(restored.packageType.id))
            assertEquals(0, device.syncPullDao().pendingCount())

            val malformed = snapshot.copy(
                documents = snapshot.documents.map {
                    if (it == oldType) it.copy(fields = JsonObject(it.fields - "isArchived")) else it
                },
            )
            assertEquals(
                UploadProblem.INVALID_DATA,
                assertFailsWith<UploadPreparationFailure> { pull.apply(malformed) }.problem,
            )
        }
    }
}
