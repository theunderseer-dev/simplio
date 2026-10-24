package com.theunderseer.simplio.feature.attendance.data.local.repository

import com.theunderseer.simplio.feature.attendance.domain.model.CreateStudentGroupRequest
import com.theunderseer.simplio.feature.attendance.domain.model.CreateStudentGroupResult
import com.theunderseer.simplio.feature.attendance.domain.model.WeeklyClassSlot
import com.theunderseer.simplio.feature.attendance.fixtures.seedSyncDefaults
import com.theunderseer.simplio.feature.attendance.fixtures.testClassSchedule
import com.theunderseer.simplio.feature.attendance.fixtures.withSyncDatabases
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class StudentGroupScheduleOverlapTest {
    private fun schedule(id: String, start: Int = 13, end: Int = 14) = testClassSchedule(
        id = id,
        name = id,
        slots = listOf(WeeklyClassSlot("tuesday", DayOfWeek.TUESDAY, LocalTime(start, 0), LocalTime(end, 0))),
    )

    @Test
    fun `overlap rejects entire group transaction without orphan rows links or queued changes`() = runTest {
        withSyncDatabases { db, _ ->
            db.seedSyncDefaults()
            val creation = RoomStudentGroupCreationRepository(db)
            val first = schedule("first")
            assertIs<CreateStudentGroupResult.Success>(
                creation.create(CreateStudentGroupRequest("First", newSchedule = first)),
            )
            val groups = db.studentGroupDao().getAll()
            val schedules = db.classScheduleDao().schedules()
            val slots = db.classScheduleDao().slots()
            val links = db.classScheduleDao().groupSchedules()
            val queue = db.syncDao().getPending(100)
            assertEquals(
                CreateStudentGroupResult.ScheduleOverlap("first"),
                creation.create(CreateStudentGroupRequest("Second", newSchedule = schedule("second", 12, 15))),
            )
            assertEquals(groups, db.studentGroupDao().getAll())
            assertEquals(schedules, db.classScheduleDao().schedules())
            assertEquals(slots, db.classScheduleDao().slots())
            assertEquals(links, db.classScheduleDao().groupSchedules())
            assertEquals(queue, db.syncDao().getPending(100))
        }
    }

    @Test
    fun `shared existing schedule touching times and nonoverlapping date periods are allowed`() = runTest {
        withSyncDatabases { db, _ ->
            db.seedSyncDefaults()
            val creation = RoomStudentGroupCreationRepository(db)
            val first = schedule("first").copy(endsOn = LocalDate(2026, 10, 6))
            assertIs<CreateStudentGroupResult.Success>(
                creation.create(CreateStudentGroupRequest("First", newSchedule = first)),
            )
            val shared = assertIs<CreateStudentGroupResult.Success>(
                creation.create(CreateStudentGroupRequest("Shared", existingScheduleId = first.id)),
            ).group
            assertEquals(listOf(first), RoomClassScheduleRepository(db).getForGroup(shared.id))
            assertIs<CreateStudentGroupResult.Success>(
                creation.create(
                    CreateStudentGroupRequest("Later", newSchedule = schedule("later", 14, 15)),
                ),
            )
            assertIs<CreateStudentGroupResult.Success>(
                creation.create(
                    CreateStudentGroupRequest(
                        "Next term",
                        newSchedule = schedule("next-term").copy(startsOn = LocalDate(2026, 10, 7)),
                    ),
                ),
            )
        }
    }

    @Test
    fun `two concurrent saves recheck occupied interval inside the transaction`() = runTest {
        withSyncDatabases { db, _ ->
            db.seedSyncDefaults()
            val creation = RoomStudentGroupCreationRepository(db)
            val a = async { creation.create(CreateStudentGroupRequest("A", newSchedule = schedule("a"))) }
            val b = async { creation.create(CreateStudentGroupRequest("B", newSchedule = schedule("b"))) }
            val results = listOf(a.await(), b.await())
            assertEquals(1, results.count { it is CreateStudentGroupResult.Success })
            assertEquals(1, results.count { it is CreateStudentGroupResult.ScheduleOverlap })
            assertEquals(3, db.studentGroupDao().getAll().size)
            assertEquals(2, db.classScheduleDao().schedules().size)
        }
    }
}
