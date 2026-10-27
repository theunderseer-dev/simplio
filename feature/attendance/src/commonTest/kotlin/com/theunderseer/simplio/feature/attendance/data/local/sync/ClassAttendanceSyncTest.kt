package com.theunderseer.simplio.feature.attendance.data.local.sync

import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomClassAttendanceRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.data.sync.ConflictChoice
import com.theunderseer.simplio.feature.attendance.fake.FakeAttendanceCloud
import com.theunderseer.simplio.feature.attendance.fixtures.ClassAttendanceTestClock
import com.theunderseer.simplio.feature.attendance.fixtures.ClassAttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.seedClassAttendanceStudents
import com.theunderseer.simplio.feature.attendance.fixtures.testUploadTarget
import com.theunderseer.simplio.feature.attendance.fixtures.withSyncDatabases
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ClassAttendanceSyncTest {
    @Test
    fun `held decision and both group students upload in one atomic batch and restore without echo`() = runTest {
        withSyncDatabases { source, device ->
            val students = source.seedClassAttendanceStudents()
            val cloud = FakeAttendanceCloud()
            cloud.uploadPending(source)
            val repository = RoomClassAttendanceRepository(source, ClassAttendanceTestClock())
            repository.save(
                repository.load(ClassAttendanceTestData.session.id),
                mapOf(students[0].student.id to 1, students[1].student.id to 2),
                false,
            )
            val store = RoomUploadStore(source)
            // A compatibility schedule may need its first catalog upload before the atomic class batch.
            val catalog = assertNotNull(store.prepare(testUploadTarget))
            val first = store.snapshot(catalog)
            if (first.documents.none { it.change.entityType == SyncEntityType.CLASS_SESSION }) {
                store.acknowledge(catalog, cloud.sendOrRecover(first, catalog.commitBody))
            }
            val batch = assertNotNull(store.prepare(testUploadTarget))
            val documents = store.snapshot(batch).documents
            assertEquals(2, documents.count { it.change.entityType == SyncEntityType.ATTENDANCE })
            assertEquals(1, documents.count { it.change.entityType == SyncEntityType.CLASS_SESSION })
            cloud.uploadPending(source)
            RoomPullStore(device, testUploadTarget).apply(cloud.snapshot())
            assertEquals("HELD", device.classSessionStateDao().get(ClassAttendanceTestData.session.id)?.status)
            assertEquals(
                3,
                device.syncPullDao().packages().sumOf { pack ->
                    device.attendanceDao().getForPackage(pack.id).sumOf { it.visitCount }
                },
            )
            assertEquals(0, device.syncPullDao().pendingCount())
        }
    }

    @Test
    fun `cancelled decision round trips without deducting credits`() = runTest {
        withSyncDatabases { source, device ->
            source.seedClassAttendanceStudents()
            val repository = RoomClassAttendanceRepository(source, ClassAttendanceTestClock())
            repository.save(repository.load(ClassAttendanceTestData.session.id), emptyMap(), true)
            val cloud = FakeAttendanceCloud()
            cloud.uploadPending(source)
            RoomPullStore(device, testUploadTarget).apply(cloud.snapshot())
            assertEquals("CANCELLED", device.classSessionStateDao().get(ClassAttendanceTestData.session.id)?.status)
            assertTrue(device.syncPullDao().packages().all { it.startedOnEpochDay == null })
        }
    }

    @Test
    fun `cancel versus attendance on either device requires one whole class choice`() = runTest {
        for (deviceCancels in listOf(true, false)) {
            for (choice in listOf(ConflictChoice.CLOUD, ConflictChoice.DEVICE)) {
                withSyncDatabases { source, device ->
                    source.seedClassAttendanceStudents()
                    val cloud = FakeAttendanceCloud()
                    cloud.uploadPending(source)
                    RoomPullStore(device, testUploadTarget).apply(cloud.snapshot())
                    val cancelling = if (deviceCancels) device else source
                    val attending = if (deviceCancels) source else device
                    val repository = RoomClassAttendanceRepository(cancelling, ClassAttendanceTestClock())
                    repository.save(repository.load(ClassAttendanceTestData.session.id), emptyMap(), true)
                    val pack = attending.syncPullDao().packages().first()
                    RoomStudentPackageRepository(attending).recordAttendance(pack.id, ClassAttendanceTestData.date, 1)
                    cloud.uploadPending(source)
                    val store = RoomConflictStore(device, testUploadTarget)
                    val review = store.create(cloud.snapshot())
                    assertEquals(1, review.items.size)
                    store.apply(review.id, review.items.associate { it.key to choice }, cloud.readHead())
                    val state = device.classSessionStateDao().get(ClassAttendanceTestData.session.id)
                    val visits = device.syncPullDao().packages().sumOf {
                        device.attendanceDao().getForPackage(it.id).sumOf { row -> row.visitCount }
                    }
                    val cancellationWins = if (choice == ConflictChoice.DEVICE) deviceCancels else !deviceCancels
                    if (cancellationWins) {
                        assertEquals("CANCELLED", state?.status)
                        assertEquals(0, visits)
                    } else {
                        assertFalse(state?.status == "CANCELLED")
                        assertEquals(1, visits)
                    }
                    cloud.uploadPending(device)
                    assertEquals(0, device.syncPullDao().pendingCount())
                    RoomPullStore(source, testUploadTarget).apply(cloud.snapshot())
                    assertEquals(state, source.classSessionStateDao().get(ClassAttendanceTestData.session.id))
                }
            }
        }
    }
}
