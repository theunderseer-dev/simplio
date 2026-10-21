package com.theunderseer.simplio.feature.attendance.data.remote

import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomClassScheduleRepository
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomPullStore
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomUploadStore
import com.theunderseer.simplio.feature.attendance.domain.schedule.sessionsBetween
import com.theunderseer.simplio.feature.attendance.fixtures.seedSyncDefaults
import com.theunderseer.simplio.feature.attendance.fixtures.testClassSchedule
import com.theunderseer.simplio.feature.attendance.fixtures.withFirestoreEmulator
import com.theunderseer.simplio.feature.attendance.fixtures.withSyncDatabases
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class FirestoreScheduleEmulatorTest {
    @Test
    fun `two devices exchange one shared schedule through real Firestore commit and snapshot query`() = runTest {
        withFirestoreEmulator { client, target ->
            withSyncDatabases { source, device ->
                source.seedSyncDefaults()
                val repository = RoomClassScheduleRepository(source)
                val schedule = testClassSchedule()
                repository.save(schedule)
                repository.assignToGroup(1, setOf(schedule.id))
                repository.assignToGroup(2, setOf(schedule.id))
                val store = RoomUploadStore(source)
                val remote = FirestoreAttendanceUploadRemote(client)
                while (true) {
                    val batch = store.prepare(target) ?: break
                    store.acknowledge(batch, remote.sendOrRecover(store.snapshot(batch), batch.commitBody))
                }
                val pull = FirestoreAttendancePullRemote(client)
                RoomPullStore(device, target).apply(pull.readSnapshot(pull.readHead()))
                val restored = RoomClassScheduleRepository(device)
                val groups = device.studentGroupDao().getAll()
                val a = restored.getForGroup(groups.first().id).single()
                val b = restored.getForGroup(groups.last().id).single()
                assertEquals(schedule, a)
                assertEquals(a, b)
                val date = LocalDate(2026, 10, 2)
                assertEquals(a.sessionsBetween(date, date), b.sessionsBetween(date, date))
                assertEquals(1, device.classScheduleDao().schedules().size)
                assertEquals(0, device.syncPullDao().pendingCount())
            }
        }
    }
}
