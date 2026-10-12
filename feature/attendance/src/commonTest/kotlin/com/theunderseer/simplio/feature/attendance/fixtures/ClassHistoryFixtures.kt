package com.theunderseer.simplio.feature.attendance.fixtures

import com.theunderseer.simplio.feature.attendance.domain.model.ClassHistoryEntry
import com.theunderseer.simplio.feature.attendance.domain.model.ClassHistoryStudent
import kotlinx.datetime.LocalDate

internal fun testClassHistoryEntry(
    date: LocalDate,
    students: List<ClassHistoryStudent> = emptyList(),
): ClassHistoryEntry = ClassHistoryEntry(
    date = date,
    students = students,
)
