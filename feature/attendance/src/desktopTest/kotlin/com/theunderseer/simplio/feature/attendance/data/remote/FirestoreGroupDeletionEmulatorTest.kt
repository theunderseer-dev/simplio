package com.theunderseer.simplio.feature.attendance.data.remote

import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomClassAttendanceRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomClassHistoryRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentGroupDeletionRepository
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomPullStore
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomUploadStore
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.ClassAttendanceTestClock
import com.theunderseer.simplio.feature.attendance.fixtures.ClassAttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.seedSyncDefaults
import com.theunderseer.simplio.feature.attendance.fixtures.withFirestoreEmulator
import com.theunderseer.simplio.feature.attendance.fixtures.withSyncDatabases
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FirestoreGroupDeletionEmulatorTest {
    @Test
    fun `deleting last groups stops their schedule on another device and keeps the held class`() = runTest {
        withFirestoreEmulator { client, target ->
            withSyncDatabases { source, device ->
                source.seedSyncDefaults()
                val clock = ClassAttendanceTestClock()
                val classes = RoomClassAttendanceRepository(source, clock)
                classes.save(classes.load(ClassAttendanceTestData.session.id), emptyMap(), false)
                val deleting = RoomStudentGroupDeletionRepository(source, clock)
                source.studentGroupDao().getAll().forEach { deleting.delete(it.id) }
                val store = RoomUploadStore(source)
                val remote = FirestoreAttendanceUploadRemote(client)
                while (true) {
                    val batch = store.prepare(target) ?: break
                    store.acknowledge(batch, remote.sendOrRecover(store.snapshot(batch), batch.commitBody))
                }
                val reader = FirestoreAttendancePullRemote(client)
                RoomPullStore(device, target).apply(reader.readSnapshot(reader.readHead()))
                assertTrue(device.studentGroupDao().getAll().isEmpty())
                assertTrue(device.classScheduleDao().schedules().all { it.archivedAtEpochMillis != null })
                val history = checkNotNull(
                    RoomClassHistoryRepository(device.attendanceDao(), device).getScheduledPage(
                        AttendanceTestData.thirdClassDate,
                        null,
                        20,
                        ClassAttendanceTestData.nextSession.endsAt,
                    ),
                )
                assertEquals(listOf(ClassAttendanceTestData.session.id), history.entries.map { it.sessionId })
                assertTrue(history.entries.none { it.canTrackAttendance })
                assertTrue(device.syncDao().getPending(100).isEmpty())
            }
        }
    }
}
