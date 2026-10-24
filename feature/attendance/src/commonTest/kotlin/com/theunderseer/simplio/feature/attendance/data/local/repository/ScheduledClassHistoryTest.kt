package com.theunderseer.simplio.feature.attendance.data.local.repository

import com.theunderseer.simplio.feature.attendance.domain.model.CreateStudentGroupRequest
import com.theunderseer.simplio.feature.attendance.domain.model.CreateStudentGroupResult
import com.theunderseer.simplio.feature.attendance.domain.model.InitialAttendance
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.WeeklyClassSlot
import com.theunderseer.simplio.feature.attendance.domain.usecase.GetClassHistoryUseCase
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.seedSyncDefaults
import com.theunderseer.simplio.feature.attendance.fixtures.testClassSchedule
import com.theunderseer.simplio.feature.attendance.fixtures.withSyncDatabases
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ScheduledClassHistoryTest {
    @Test
    fun `same date sessions stay on one page and shared group students use one class`() = runTest {
        withSyncDatabases { database, _ ->
            database.seedSyncDefaults()
            val groups = RoomStudentGroupCreationRepository(database)
            val date = LocalDate(2026, 10, 2)
            var sharedId = ""
            repeat(6) { index ->
                val schedule = testClassSchedule(
                    id = "series-$index",
                    name = "Class $index",
                    slots = listOf(
                        WeeklyClassSlot("friday", DayOfWeek.FRIDAY, LocalTime(11 + index, 0), LocalTime(12 + index, 0)),
                    ),
                )
                groups.create(CreateStudentGroupRequest("Group $index", newSchedule = schedule))
                sharedId = schedule.id
            }
            val a = assertIs<CreateStudentGroupResult.Success>(
                groups.create(
                    CreateStudentGroupRequest("Shared A", existingScheduleId = sharedId),
                ),
            ).group
            val b = assertIs<CreateStudentGroupResult.Success>(
                groups.create(
                    CreateStudentGroupRequest("Shared B", existingScheduleId = sharedId),
                ),
            ).group
            val type = database.packageTypeDao().getAll().first()
            val plan =
                PackageType(
                    type.id,
                    type.name,
                    type.totalClasses,
                    type.validityWeeks,
                    type.price,
                    type.maxVisitsPerAttendance,
                )
            listOf(a, b).forEach { group ->
                val student = RoomStudentRepository(database).create(group.name, group.id, "")
                val pack = RoomStudentPackageRepository(
                    database,
                ).create(student.id, plan, date, InitialAttendance(date, 1))
                assertNotNull(database.attendanceDao().getForPackage(pack.id).single().sessionId)
            }
            val history = GetClassHistoryUseCase(RoomClassHistoryRepository(database.attendanceDao(), database))
            val first = history(date)
            assertEquals(7, first.entries.size)
            assertNull(first.nextBeforeDate)
            val all = first.entries
            assertEquals(7, all.size)
            assertEquals(7, all.map { it.key }.distinct().size)
            assertEquals(all.mapNotNull { it.startsAt }.sortedDescending(), all.mapNotNull { it.startsAt })
            assertEquals(2, all.single { it.sessionId?.startsWith(sharedId + ":") == true }.students.size)
        }
    }

    @Test
    fun `pages contain five complete dates including every class on boundary date`() = runTest {
        withSyncDatabases { database, _ ->
            database.seedSyncDefaults()
            val creation = RoomStudentGroupCreationRepository(database)
            repeat(6) { index ->
                val group = assertIs<CreateStudentGroupResult.Success>(
                    creation.create(
                        CreateStudentGroupRequest(
                            "Group $index",
                            newSchedule = testClassSchedule(
                                id = "series-$index",
                                slots = listOf(
                                    WeeklyClassSlot(
                                        "monday",
                                        DayOfWeek.MONDAY,
                                        LocalTime(11 + index, 0),
                                        LocalTime(12 + index, 0),
                                    ),
                                    WeeklyClassSlot(
                                        "friday",
                                        DayOfWeek.FRIDAY,
                                        LocalTime(11 + index, 0),
                                        LocalTime(12 + index, 0),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ).group
                val student = RoomStudentRepository(database).create("Student $index", group.id, "")
                val plan = AttendanceTestData.fourClassPackage.copy(id = database.packageTypeDao().getAll().first().id)
                RoomStudentPackageRepository(database).create(
                    student.id,
                    plan,
                    AttendanceTestData.firstClassDate,
                    InitialAttendance(AttendanceTestData.firstClassDate, 1),
                )
            }
            val history = GetClassHistoryUseCase(RoomClassHistoryRepository(database.attendanceDao(), database))
            val first = history(AttendanceTestData.eighthClassDate)
            assertEquals(
                listOf(
                    AttendanceTestData.eighthClassDate,
                    AttendanceTestData.seventhClassDate,
                    AttendanceTestData.sixthClassDate,
                    AttendanceTestData.fifthClassDate,
                    AttendanceTestData.fourthClassDate,
                ),
                first.entries.map { it.date }.distinct(),
            )
            assertEquals(35, first.entries.size)
            assertEquals(7, first.entries.count { it.date == first.nextBeforeDate })
            val second = history(AttendanceTestData.eighthClassDate, first.nextBeforeDate)
            assertEquals(
                listOf(
                    AttendanceTestData.thirdClassDate,
                    AttendanceTestData.secondClassDate,
                    AttendanceTestData.firstClassDate,
                ),
                second.entries.map { it.date }.distinct(),
            )
            assertEquals(21, second.entries.size)
            assertEquals(56, (first.entries + second.entries).map { it.key }.distinct().size)
            assertNull(second.nextBeforeDate)
        }
    }

    @Test
    fun `custom group cannot record or buy package on another schedule weekday`() = runTest {
        withSyncDatabases { database, _ ->
            database.seedSyncDefaults()
            val group = assertIs<CreateStudentGroupResult.Success>(
                RoomStudentGroupCreationRepository(database).create(
                    CreateStudentGroupRequest(
                        "Tuesday",
                        newSchedule = testClassSchedule(
                            slots = listOf(
                                WeeklyClassSlot("tuesday", DayOfWeek.TUESDAY, LocalTime(9, 0), LocalTime(10, 0)),
                            ),
                        ),
                    ),
                ),
            ).group
            val student = RoomStudentRepository(database).create("Private student", group.id, "")
            val type = database.packageTypeDao().getAll().first()
            val plan =
                PackageType(
                    type.id,
                    type.name,
                    type.totalClasses,
                    type.validityWeeks,
                    type.price,
                    type.maxVisitsPerAttendance,
                )
            assertFailsWith<IllegalArgumentException> {
                RoomStudentPackageRepository(database).create(student.id, plan, LocalDate(2026, 10, 2), null)
            }
            assertEquals(emptyList(), database.studentPackageDao().getForStudent(student.id))
            val pack = RoomStudentPackageRepository(database).create(student.id, plan, LocalDate(2026, 10, 6), null)
            assertFailsWith<IllegalArgumentException> {
                RoomStudentPackageRepository(database).recordAttendance(pack.id, LocalDate(2026, 10, 9), 1)
            }
            assertEquals(emptyList(), database.attendanceDao().getForPackage(pack.id))
        }
    }
}
