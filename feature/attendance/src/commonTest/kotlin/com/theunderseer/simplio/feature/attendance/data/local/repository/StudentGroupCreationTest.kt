package com.theunderseer.simplio.feature.attendance.data.local.repository

import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomSyncOutbox
import com.theunderseer.simplio.feature.attendance.domain.model.CreateStudentGroupRequest
import com.theunderseer.simplio.feature.attendance.domain.model.CreateStudentGroupResult
import com.theunderseer.simplio.feature.attendance.domain.model.WeeklyClassSlot
import com.theunderseer.simplio.feature.attendance.domain.schedule.DefaultClassSchedule
import com.theunderseer.simplio.feature.attendance.fixtures.seedSyncDefaults
import com.theunderseer.simplio.feature.attendance.fixtures.testClassSchedule
import com.theunderseer.simplio.feature.attendance.fixtures.withSyncDatabases
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class StudentGroupCreationTest {
    private val freeSchedule = testClassSchedule(
        slots = listOf(WeeklyClassSlot("friday", DayOfWeek.FRIDAY, LocalTime(11, 0), LocalTime(12, 0))),
    )

    @Test
    fun `create group and series atomically then reuse same series for another group`() = runTest {
        withSyncDatabases { database, _ ->
            database.seedSyncDefaults()
            val repository = RoomStudentGroupCreationRepository(database)
            val schedule = freeSchedule
            val a = assertIs<CreateStudentGroupResult.Success>(
                repository.create(
                    CreateStudentGroupRequest("  Private   yoga ", newSchedule = schedule),
                ),
            ).group
            val b = assertIs<CreateStudentGroupResult.Success>(
                repository.create(
                    CreateStudentGroupRequest("Evening group", existingScheduleId = schedule.id),
                ),
            ).group
            assertEquals("Private yoga", a.name)
            assertEquals(listOf(schedule), RoomClassScheduleRepository(database).getForGroup(a.id))
            assertEquals(listOf(schedule), RoomClassScheduleRepository(database).getForGroup(b.id))
            assertEquals(2, database.classScheduleDao().schedules().size)
        }
    }

    @Test
    fun `case and repeated whitespace cannot create another group or orphan schedule`() = runTest {
        withSyncDatabases { database, _ ->
            database.seedSyncDefaults()
            val repository = RoomStudentGroupCreationRepository(database)
            repository.create(CreateStudentGroupRequest("Private yoga", newSchedule = freeSchedule))
            val pending = RoomSyncOutbox(database).getPending()
            val schedules = database.classScheduleDao().schedules()
            assertEquals(
                CreateStudentGroupResult.DuplicateName,
                repository.create(
                    CreateStudentGroupRequest("  PRIVATE   yoga  ", newSchedule = testClassSchedule(id = "unused")),
                ),
            )
            assertEquals(pending, RoomSyncOutbox(database).getPending())
            assertEquals(schedules, database.classScheduleDao().schedules())
        }
    }

    @Test
    fun `concurrent creation checks name inside transaction and only one succeeds`() = runTest {
        withSyncDatabases { database, _ ->
            database.seedSyncDefaults()
            val repository = RoomStudentGroupCreationRepository(database)
            val request = CreateStudentGroupRequest("Concurrent", existingScheduleId = DefaultClassSchedule.ID)
            val first = async { repository.create(request) }
            val second = async { repository.create(request.copy(name = " concurrent ")) }
            val results = listOf(first.await(), second.await())
            assertEquals(1, results.count { it is CreateStudentGroupResult.Success })
            assertEquals(1, results.count { it == CreateStudentGroupResult.DuplicateName })
        }
    }

    @Test
    fun `invalid schedule and invalid name do not change rows or queue`() = runTest {
        withSyncDatabases { database, _ ->
            database.seedSyncDefaults()
            val repository = RoomStudentGroupCreationRepository(database)
            val before = RoomSyncOutbox(database).getPending()
            assertEquals(
                CreateStudentGroupResult.InvalidName,
                repository.create(
                    CreateStudentGroupRequest(" ", existingScheduleId = DefaultClassSchedule.ID),
                ),
            )
            assertEquals(
                CreateStudentGroupResult.InvalidSchedule,
                repository.create(
                    CreateStudentGroupRequest("Missing", existingScheduleId = "missing"),
                ),
            )
            assertEquals(before, RoomSyncOutbox(database).getPending())
            assertEquals(2, database.studentGroupDao().getAll().size)
        }
    }
}
