package com.theunderseer.simplio.feature.attendance.data.local.sync

import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomPackageTypeCreationRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentRepository
import com.theunderseer.simplio.feature.attendance.data.local.transaction.RoomTransactionRunner
import com.theunderseer.simplio.feature.attendance.data.sync.ConflictChoice
import com.theunderseer.simplio.feature.attendance.data.sync.InvalidConflictChoice
import com.theunderseer.simplio.feature.attendance.data.sync.UploadPreparationFailure
import com.theunderseer.simplio.feature.attendance.data.sync.UploadProblem
import com.theunderseer.simplio.feature.attendance.domain.model.CreatePackageTypeResult
import com.theunderseer.simplio.feature.attendance.domain.result.RenewStudentPackageResult
import com.theunderseer.simplio.feature.attendance.domain.usecase.RenewStudentPackageUseCase
import com.theunderseer.simplio.feature.attendance.fake.FakeAttendanceCloud
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.registerCompletedSyncStudent
import com.theunderseer.simplio.feature.attendance.fixtures.registerSyncStudent
import com.theunderseer.simplio.feature.attendance.fixtures.seedSyncDefaults
import com.theunderseer.simplio.feature.attendance.fixtures.testCreatePackageTypeRequest
import com.theunderseer.simplio.feature.attendance.fixtures.testUploadTarget
import com.theunderseer.simplio.feature.attendance.fixtures.withSyncDatabases
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CustomPackageSyncTest {
    @Test
    fun `renew into custom unlimited package and round trip purchase snapshots with no echo`() = runTest {
        withSyncDatabases { source, device ->
            val previous = source.registerCompletedSyncStudent()
            val type = assertIs<CreatePackageTypeResult.Success>(
                RoomPackageTypeCreationRepository(source).create(testCreatePackageTypeRequest()),
            ).packageType
            val renewed = assertIs<RenewStudentPackageResult.Success>(
                RenewStudentPackageUseCase(
                    RoomStudentPackageRepository(source),
                    RoomStudentRepository(source),
                    RoomTransactionRunner(source),
                )(
                    studentId = previous.student.id,
                    previousPackageId = previous.studentPackage.id,
                    packageType = type,
                    renewedOn = AttendanceTestData.fifthClassDate,
                    note = AttendanceTestData.studentNote,
                ),
            ).studentPackage
            assertNull(renewed.expiresOn)
            val cloud = FakeAttendanceCloud()
            cloud.uploadPending(source)
            device.seedSyncDefaults()
            RoomPullStore(device, testUploadTarget).apply(cloud.snapshot())
            val student = device.studentDao().getAll().single()
            val restored = assertNotNull(RoomStudentPackageRepository(device).getLatestPackageForStudent(student.id))
            assertNull(restored.packageType.validityWeeks)
            assertNull(restored.expiresOn)
            assertEquals(type.name, restored.packageType.name)
            assertEquals(1, restored.usedClasses)
            assertEquals(AttendanceTestData.fifthClassDate, restored.startedOn)
            assertEquals(2, device.studentPackageDao().getForStudent(student.id).size)
            assertEquals(AttendanceTestData.studentNote, student.note)
            assertNull(RoomUploadStore(device).prepare(testUploadTarget))
        }
    }

    @Test
    fun `unlimited first attendance removal and later reactivation preserve catalog terms`() = runTest {
        withSyncDatabases { source, device ->
            source.seedSyncDefaults()
            val type = assertIs<CreatePackageTypeResult.Success>(
                RoomPackageTypeCreationRepository(source).create(testCreatePackageTypeRequest()),
            ).packageType
            val registered = source.registerSyncStudent(packageType = type)
            val repository = RoomStudentPackageRepository(source)
            repository.removeAttendance(registered.studentPackage.id, AttendanceTestData.firstClassDate)
            val awaiting = assertNotNull(repository.getLatestPackageForStudent(registered.student.id))
            assertNull(awaiting.startedOn)
            assertNull(awaiting.expiresOn)
            repository.recordAttendance(awaiting.id, AttendanceTestData.thirdClassDate, 1)
            val active = assertNotNull(repository.getLatestPackageForStudent(registered.student.id))
            assertEquals(AttendanceTestData.thirdClassDate, active.startedOn)
            assertEquals(1, active.usedClasses)
            assertNull(active.expiresOn)
            val cloud = FakeAttendanceCloud()
            cloud.uploadPending(source)
            RoomPullStore(device, testUploadTarget).apply(cloud.snapshot())
            val student = device.studentDao().getAll().single()
            val remote = assertNotNull(RoomStudentPackageRepository(device).getLatestPackageForStudent(student.id))
            assertEquals(setOf(AttendanceTestData.thirdClassDate), remote.attendanceDates)
            assertNull(remote.expiresOn)
        }
    }

    @Test
    fun `offline catalog name collision requires rename and preserves both packages`() = runTest {
        withSyncDatabases { source, device ->
            source.seedSyncDefaults()
            val cloud = FakeAttendanceCloud()
            cloud.uploadPending(source)
            RoomPullStore(device, testUploadTarget).apply(cloud.snapshot())
            val request = testCreatePackageTypeRequest()
            RoomPackageTypeCreationRepository(source).create(request)
            RoomPackageTypeCreationRepository(device).create(request.copy(price = 10000))
            cloud.uploadPending(source)
            val store = RoomConflictStore(device, testUploadTarget)
            val review = store.create(cloud.snapshot())
            val item = review.items.single()
            assertTrue(item.requiresPackageRename)
            val choice = mapOf(item.key to ConflictChoice.DEVICE)
            assertFailsWith<InvalidConflictChoice> { store.apply(review.id, choice, cloud.readHead()) }
            val renamed = AttendanceTestData.privateThreeWeeklyPackage.name
            store.apply(review.id, choice, cloud.readHead(), mapOf(item.key to renamed))
            cloud.uploadPending(device)
            RoomPullStore(source, testUploadTarget).apply(cloud.snapshot())
            val types = source.packageTypeDao().getAll().associateBy { it.name }
            assertEquals(request.price, types.getValue(request.name).price)
            assertEquals(10000, types.getValue(renamed).price)
            assertNull(types.getValue(renamed).validityWeeks)
        }
    }

    @Test
    fun `missing malformed or legacy null validity cannot erase local data or become unlimited`() = runTest {
        withSyncDatabases { source, device ->
            source.registerSyncStudent()
            val cloud = FakeAttendanceCloud()
            cloud.uploadPending(source)
            device.seedSyncDefaults()
            val pending = RoomSyncOutbox(device).getPending()
            val snapshot = cloud.snapshot()
            val brokenValues = listOf(
                null,
                buildJsonObject { put("stringValue", "unlimited") },
                buildJsonObject { put("integerValue", "0") },
                buildJsonObject { put("integerValue", "99999999999999999999") },
                buildJsonObject { put("nullValue", JsonNull) },
            )
            for (type in listOf(SyncEntityType.PACKAGE_TYPE, SyncEntityType.STUDENT_PACKAGE)) {
                for (value in brokenValues) {
                    val invalid = snapshot.copy(
                        documents = snapshot.documents.map { document ->
                            if (document.type != type) {
                                document
                            } else {
                                document.copy(
                                    fields = JsonObject(
                                        document.fields.toMutableMap().apply {
                                            if (value == null) remove("validityWeeks") else put("validityWeeks", value)
                                        },
                                    ),
                                )
                            }
                        },
                    )
                    val failure = assertFailsWith<UploadPreparationFailure> {
                        RoomPullStore(device, testUploadTarget).apply(invalid)
                    }
                    assertEquals(UploadProblem.INVALID_DATA, failure.problem)
                    assertEquals(0, device.studentDao().count())
                    assertEquals(pending, RoomSyncOutbox(device).getPending())
                    assertNull(device.syncUploadDao().getState())
                }
            }
        }
    }

    @Test
    fun `purchase snapshot stays unlimited when its catalog template changes terms`() = runTest {
        withSyncDatabases { source, device ->
            source.seedSyncDefaults()
            val type = assertIs<CreatePackageTypeResult.Success>(
                RoomPackageTypeCreationRepository(source).create(testCreatePackageTypeRequest()),
            ).packageType
            source.registerSyncStudent(packageType = type)
            val cloud = FakeAttendanceCloud()
            cloud.uploadPending(source)
            val snapshot = cloud.snapshot()
            val changed = snapshot.copy(
                documents = snapshot.documents.map { document ->
                    if (document.type != SyncEntityType.PACKAGE_TYPE || document.string("name") != type.name) {
                        document
                    } else {
                        document.copy(
                            fields = buildJsonObject {
                                document.fields.forEach { (key, value) -> put(key, value) }
                                put("validityWeeks", buildJsonObject { put("integerValue", "4") })
                                put("totalClasses", buildJsonObject { put("integerValue", "20") })
                                put("price", buildJsonObject { put("integerValue", (type.price + 1000).toString()) })
                            },
                        )
                    }
                },
            )
            RoomPullStore(device, testUploadTarget).apply(changed)
            val student = device.studentDao().getAll().single()
            val purchase = assertNotNull(
                RoomStudentPackageRepository(device).getLatestPackageForStudent(student.id),
            )
            assertEquals(type.copy(id = purchase.packageType.id), purchase.packageType)
            assertEquals(4, device.packageTypeDao().getById(purchase.packageType.id)?.validityWeeks)
            assertNull(purchase.expiresOn)
        }
    }
}
