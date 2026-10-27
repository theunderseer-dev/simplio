package com.theunderseer.simplio.feature.attendance.fake

import com.theunderseer.simplio.feature.attendance.domain.model.ClassAttendanceFailure
import com.theunderseer.simplio.feature.attendance.domain.model.ClassAttendanceProblem
import com.theunderseer.simplio.feature.attendance.domain.model.ClassAttendanceSheet
import com.theunderseer.simplio.feature.attendance.domain.repository.ClassAttendanceRepository
import com.theunderseer.simplio.feature.attendance.fixtures.testClassAttendanceSheet

internal class FakeClassAttendanceRepository(var sheet: ClassAttendanceSheet = testClassAttendanceSheet()) :
    ClassAttendanceRepository {
    var saves = 0
    var problem: ClassAttendanceProblem? = null
    var savedVisits: Map<Int, Int>? = null
    var cancelled = false
    override suspend fun load(sessionId: String): ClassAttendanceSheet = sheet
    override suspend fun save(expected: ClassAttendanceSheet, visitsByStudent: Map<Int, Int>, cancel: Boolean) {
        problem?.let { throw ClassAttendanceFailure(it) }
        saves++
        savedVisits = visitsByStudent.toMap()
        cancelled = cancel
    }
}
