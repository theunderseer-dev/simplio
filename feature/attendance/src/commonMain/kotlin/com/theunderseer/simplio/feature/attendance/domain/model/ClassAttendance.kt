package com.theunderseer.simplio.feature.attendance.domain.model

enum class ClassSessionStatus { UNRECORDED, HELD, CANCELLED }

data class ClassAttendanceRow(
    val student: Student,
    val studentPackage: StudentPackage?,
    val recordedVisitCount: Int,
    val canRecord: Boolean,
    val canRenew: Boolean,
) {
    val maximumVisitCount: Int get() = studentPackage?.let {
        minOf(it.packageType.maxVisitsPerAttendance, it.remainingClasses + recordedVisitCount)
    } ?: 0
}

/** An immutable snapshot used to reject stale saves without losing the user's draft. */
data class ClassAttendanceSheet(
    val session: ClassSession,
    val timeLabel: String,
    val rows: List<ClassAttendanceRow>,
    val groupIds: Set<Int>,
) {
    val canCancel: Boolean get() = rows.none { it.recordedVisitCount > 0 }
}

enum class ClassAttendanceProblem { UNAVAILABLE, STALE, INVALID_SELECTION, HAS_ATTENDANCE, CANCELLED }
class ClassAttendanceFailure(val problem: ClassAttendanceProblem) : IllegalStateException()
