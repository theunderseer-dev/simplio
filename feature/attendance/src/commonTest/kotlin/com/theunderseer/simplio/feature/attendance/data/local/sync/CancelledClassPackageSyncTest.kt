package com.theunderseer.simplio.feature.attendance.data.local.sync

import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomClassAttendanceRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.data.remote.FirestoreUploadProtocol
import com.theunderseer.simplio.feature.attendance.data.sync.UploadPreparationFailure
import com.theunderseer.simplio.feature.attendance.data.sync.UploadProblem
import com.theunderseer.simplio.feature.attendance.fake.FakeAttendanceCloud
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.ClassAttendanceTestClock
import com.theunderseer.simplio.feature.attendance.fixtures.ClassAttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.TEST_UPLOAD_PROTOCOL_VERSION
import com.theunderseer.simplio.feature.attendance.fixtures.seedClassAttendanceStudents
import com.theunderseer.simplio.feature.attendance.fixtures.testUploadTarget
import com.theunderseer.simplio.feature.attendance.fixtures.withSyncDatabases
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CancelledClassPackageSyncTest {
    @Test
    fun `cancellation uploads all grants atomically and repeat pulls do not add days or echo`() = runTest {
        withSyncDatabases { source, device ->
            source.seedClassAttendanceStudents(activated = true)
            val cloud = FakeAttendanceCloud()
            cloud.uploadPending(source)
            val repository = RoomClassAttendanceRepository(source, ClassAttendanceTestClock())
            repository.save(repository.load(ClassAttendanceTestData.session.id), emptyMap(), true)
            val store = RoomUploadStore(source)
            var batch = assertNotNull(store.prepare(testUploadTarget))
            while (store.snapshot(batch).documents.none { it.change.entityType == SyncEntityType.CLASS_SESSION }) {
                store.acknowledge(batch, cloud.sendOrRecover(store.snapshot(batch), batch.commitBody))
                batch = assertNotNull(store.prepare(testUploadTarget))
            }
            val snapshot = store.snapshot(batch)
            assertEquals(TEST_UPLOAD_PROTOCOL_VERSION, snapshot.protocolVersion)
            val packages = snapshot.documents.filter { it.change.entityType == SyncEntityType.STUDENT_PACKAGE }
            assertEquals(2, packages.size)
            assertTrue(packages.all { "validityExtensions" in it.fields })
            assertEquals(1, snapshot.documents.count { it.change.entityType == SyncEntityType.CLASS_SESSION })
            cloud.uploadPending(source)
            val imported = cloud.snapshot()
            repeat(2) {
                RoomPullStore(device, testUploadTarget).apply(imported)
                assertEquals(0, device.syncPullDao().pendingCount())
                device.studentDao().getAll().forEach { student ->
                    val pack = RoomStudentPackageRepository(device).getLatestPackageForStudent(student.id)!!
                    assertEquals(AttendanceTestData.class10Date, pack.expiresOn)
                    assertEquals(mapOf(ClassAttendanceTestData.session.id to 3), pack.validityExtensions)
                }
                assertEquals(3, device.attendanceDao().getAll().sumOf { it.visitCount })
            }
            val student = source.studentDao().getAll().first()
            val packagesRepository = RoomStudentPackageRepository(source)
            val packageId = packagesRepository.getLatestPackageForStudent(student.id)!!.id
            packagesRepository.recordAttendance(packageId, AttendanceTestData.fourthClassDate, 1)
            packagesRepository.removeAttendance(packageId, AttendanceTestData.firstClassDate)
            cloud.uploadPending(source)
            RoomPullStore(device, testUploadTarget).apply(cloud.snapshot())
            val restoredStudent = device.studentDao().getAll().single { it.fullName == student.fullName }
            val corrected = RoomStudentPackageRepository(device).getLatestPackageForStudent(restoredStudent.id)!!
            assertEquals(AttendanceTestData.class13Date, corrected.expiresOn)
            assertEquals(mapOf(ClassAttendanceTestData.session.id to 4), corrected.validityExtensions)
        }
    }

    @Test
    fun `pull rejects orphan nonpositive and downgraded grants without partial import`() = runTest {
        withSyncDatabases { source, device ->
            source.seedClassAttendanceStudents(activated = true)
            val repository = RoomClassAttendanceRepository(source, ClassAttendanceTestClock())
            repository.save(repository.load(ClassAttendanceTestData.session.id), emptyMap(), true)
            val cloud = FakeAttendanceCloud()
            cloud.uploadPending(source)
            val snapshot = cloud.snapshot()
            val document = snapshot.documents.first { it.type == SyncEntityType.STUDENT_PACKAGE }
            val invalidFields = listOf(
                document.fields + (
                    "validityExtensions" to buildJsonObject {
                        put(
                            "mapValue",
                            buildJsonObject {
                                put(
                                    "fields",
                                    buildJsonObject {
                                        put(ClassAttendanceTestData.nextSession.id, FirestoreUploadProtocol.integer(3))
                                    },
                                )
                            },
                        )
                    }
                    ),
                document.fields + (
                    "validityExtensions" to buildJsonObject {
                        put(
                            "mapValue",
                            buildJsonObject {
                                put(
                                    "fields",
                                    buildJsonObject {
                                        put(ClassAttendanceTestData.session.id, FirestoreUploadProtocol.integer(0))
                                    },
                                )
                            },
                        )
                    }
                    ),
                document.fields + ("schemaVersion" to FirestoreUploadProtocol.integer(1)),
            )
            invalidFields.forEach { fields ->
                val invalid = snapshot.copy(
                    documents = snapshot.documents.map {
                        if (it == document) it.copy(fields = JsonObject(fields)) else it
                    },
                )
                assertEquals(
                    UploadProblem.INVALID_DATA,
                    assertFailsWith<UploadPreparationFailure> {
                        RoomPullStore(device, testUploadTarget).apply(invalid)
                    }.problem,
                )
                assertTrue(device.syncPullDao().packages().isEmpty())
                assertTrue(device.classSessionStateDao().getAll().isEmpty())
            }
        }
    }
}
