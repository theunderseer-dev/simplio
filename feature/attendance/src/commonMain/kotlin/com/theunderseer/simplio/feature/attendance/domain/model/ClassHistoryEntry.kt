package com.theunderseer.simplio.feature.attendance.domain.model

import kotlinx.datetime.LocalDate

data class ClassHistoryEntry(val date: LocalDate, val students: List<ClassHistoryStudent>)

data class ClassHistoryStudent(val student: Student, val visitCount: Int)
