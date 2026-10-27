package com.theunderseer.simplio.feature.attendance.data.remote

import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomClassAttendanceRepository
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomPullStore
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomUploadStore
import com.theunderseer.simplio.feature.attendance.fixtures.ClassAttendanceTestClock
import com.theunderseer.simplio.feature.attendance.fixtures.ClassAttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.seedClassAttendanceStudents
import com.theunderseer.simplio.feature.attendance.fixtures.withFirestoreEmulator
import com.theunderseer.simplio.feature.attendance.fixtures.withSyncDatabases
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FirestoreClassAttendanceEmulatorTest {
    @Test
    fun `held and cancelled classes restore on another device through actual Firestore commits`() = runTest {
        withFirestoreEmulator { client, target ->
            withSyncDatabases { source, device ->
                val students = source.seedClassAttendanceStudents()
                val clock = ClassAttendanceTestClock()
                val repository = RoomClassAttendanceRepository(source, clock)
                repository.save(
                    repository.load(ClassAttendanceTestData.session.id),
                    mapOf(students[0].student.id to 1, students[1].student.id to 2),
                    false,
                )
                clock.instant = ClassAttendanceTestData.nextSession.endsAt
                repository.save(repository.load(ClassAttendanceTestData.nextSession.id), emptyMap(), true)
                val store = RoomUploadStore(source)
                val remote = FirestoreAttendanceUploadRemote(client)
                while (true) {
                    val batch = store.prepare(target) ?: break
                    store.acknowledge(batch, remote.sendOrRecover(store.snapshot(batch), batch.commitBody))
                }
                val reader = FirestoreAttendancePullRemote(client)
                RoomPullStore(device, target).apply(reader.readSnapshot(reader.readHead()))
                assertEquals("HELD", device.classSessionStateDao().get(ClassAttendanceTestData.session.id)?.status)
                assertEquals(
                    "CANCELLED",
                    device.classSessionStateDao().get(ClassAttendanceTestData.nextSession.id)?.status,
                )
                assertEquals(
                    3,
                    device.syncPullDao().packages().sumOf {
                        device.attendanceDao().getForPackage(it.id).sumOf { visit -> visit.visitCount }
                    },
                )
                assertTrue(device.syncDao().getPending(10).isEmpty())
            }
        }
    }
}
