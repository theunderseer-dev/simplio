package com.theunderseer.simplio.feature.attendance.domain.repository

import com.theunderseer.simplio.feature.attendance.domain.model.ClassAttendanceSheet

interface ClassAttendanceRepository {
    suspend fun load(sessionId: String): ClassAttendanceSheet
    suspend fun save(expected: ClassAttendanceSheet, visitsByStudent: Map<Int, Int>, cancel: Boolean)
}
