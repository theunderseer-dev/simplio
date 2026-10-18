package com.theunderseer.simplio.feature.attendance.data.local.repository

import com.theunderseer.simplio.feature.attendance.data.local.dao.AttendanceDao
import com.theunderseer.simplio.feature.attendance.domain.model.ClassHistoryEntry
import com.theunderseer.simplio.feature.attendance.domain.model.ClassHistoryStudent
import com.theunderseer.simplio.feature.attendance.domain.model.Student
import com.theunderseer.simplio.feature.attendance.domain.repository.ClassHistoryRepository
import kotlinx.datetime.LocalDate

class RoomClassHistoryRepository(private val attendanceDao: AttendanceDao) : ClassHistoryRepository {
    override suspend fun getFirstAttendanceDate(): LocalDate? = attendanceDao
        .getFirstAttendanceEpochDay()
        ?.let { LocalDate.fromEpochDays(it) }

    override suspend fun getPage(beforeDate: LocalDate?, limit: Int): List<ClassHistoryEntry> = attendanceDao
        .getClassHistoryPage(
            beforeEpochDay = beforeDate?.toEpochDays(),
            limit = limit,
        )
        .groupBy { it.attendedOnEpochDay }
        .map { (epochDay, rows) ->
            ClassHistoryEntry(
                date = LocalDate.fromEpochDays(epochDay),
                students = rows.map { row ->
                    ClassHistoryStudent(
                        student = Student(
                            id = row.studentId,
                            fullName = row.fullName,
                            groupId = row.groupId,
                        ),
                        visitCount = row.visitCount,
                    )
                },
            )
        }
}
