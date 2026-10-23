package com.theunderseer.simplio.feature.attendance.data.local.model

data class ClassAttendanceRow(
    val attendedOnEpochDay: Long,
    val studentId: Int,
    val fullName: String,
    val groupId: Int,
    val visitCount: Int,
    val sessionId: String? = null,
)
