package com.theunderseer.simplio.feature.attendance.domain.usecase

import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentRepository
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomSyncOutbox
import com.theunderseer.simplio.feature.attendance.data.local.transaction.RoomTransactionRunner
import com.theunderseer.simplio.feature.attendance.domain.model.InvalidStudentName
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.seedSyncDefaults
import com.theunderseer.simplio.feature.attendance.fixtures.withSyncDatabases
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StudentRegistrationValidationTest {
    @Test
    fun `invalid names are rejected before student package or outbox writes while companion names and notes persist`() =
        runTest {
            withSyncDatabases { database, _ ->
                database.seedSyncDefaults()
                val students = RoomStudentRepository(database)
                val register = RegisterStudentUseCase(
                    students,
                    RoomStudentPackageRepository(database),
                    RoomTransactionRunner(database),
                )
                val before = RoomSyncOutbox(database).getPending()
                val overlongName = "x".repeat(201)
                for (name in listOf("", "   ", "Anna\nSmith", "Anna\tSmith", overlongName)) {
                    assertFailsWith<InvalidStudentName> {
                        register(
                            name,
                            AttendanceTestData.student.groupId,
                            AttendanceTestData.fourClassPackage,
                            AttendanceTestData.registrationDate,
                        )
                    }
                    assertFailsWith<InvalidStudentName> {
                        students.create(name, AttendanceTestData.student.groupId, "")
                    }
                    assertEquals(0, database.studentDao().count())
                    assertEquals(emptyList(), database.syncPullDao().packages())
                    assertEquals(before, RoomSyncOutbox(database).getPending())
                }
                val result = register(
                    "  ${AttendanceTestData.studentWithCompanionName}  ",
                    AttendanceTestData.student.groupId,
                    AttendanceTestData.fourClassPackage,
                    AttendanceTestData.registrationDate,
                    recordFirstAttendance = false,
                    note = AttendanceTestData.unrestrictedNote,
                )
                assertEquals(AttendanceTestData.studentWithCompanionName, result.student.fullName)
                assertEquals(AttendanceTestData.unrestrictedNote, result.student.note)
            }
        }
}
