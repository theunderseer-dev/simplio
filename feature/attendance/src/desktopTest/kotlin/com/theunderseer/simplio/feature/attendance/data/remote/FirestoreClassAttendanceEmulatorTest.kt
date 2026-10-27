package com.theunderseer.simplio.feature.attendance.data.remote

import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomClassAttendanceRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomPullStore
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomUploadStore
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.ClassAttendanceTestClock
import com.theunderseer.simplio.feature.attendance.fixtures.ClassAttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.classStudentActions
import com.theunderseer.simplio.feature.attendance.fixtures.seedClassAttendanceStudents
import com.theunderseer.simplio.feature.attendance.fixtures.withFirestoreEmulator
import com.theunderseer.simplio.feature.attendance.fixtures.withSyncDatabases
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FirestoreClassAttendanceEmulatorTest {
    @Test
    fun `registration and renewal restore without finalizing the attendance draft`() = runTest {
        withFirestoreEmulator { client, target ->
            withSyncDatabases { source, device ->
                val student = source.seedClassAttendanceStudents(standardCapacity = 1).first()
                RoomStudentPackageRepository(
                    source,
                ).recordAttendance(student.studentPackage.id, AttendanceTestData.firstClassDate, 1)
                val repository = RoomClassAttendanceRepository(source, ClassAttendanceTestClock())
                val sheet = source.classStudentActions().renew(
                    repository.load(ClassAttendanceTestData.session.id),
                    student.student.id,
                    student.studentPackage.packageType,
                    1,
                    true,
                    "",
                )
                source.classStudentActions().register(
                    sheet,
                    ClassAttendanceTestData.companionName,
                    student.student.groupId,
                    student.studentPackage.packageType,
                    1,
                    false,
                    "",
                )
                val store = RoomUploadStore(source)
                val remote = FirestoreAttendanceUploadRemote(client)
                while (true) {
                    val batch = store.prepare(target) ?: break
                    store.acknowledge(batch, remote.sendOrRecover(store.snapshot(batch), batch.commitBody))
                }
                val reader = FirestoreAttendancePullRemote(client)
                RoomPullStore(device, target).apply(reader.readSnapshot(reader.readHead()))
                assertEquals(3, device.studentDao().getAll().size)
                assertEquals(4, device.syncPullDao().packages().size)
                assertEquals(2, device.attendanceDao().getAll().sumOf { it.visitCount })
                assertTrue(device.classSessionStateDao().getAll().isEmpty())
                assertTrue(device.syncDao().getPending(10).isEmpty())
            }
        }
    }

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
