package com.theunderseer.simplio.feature.attendance.domain.model

/** Exactly one of an existing series or a new series is selected. */
data class CreateStudentGroupRequest(
    val name: String,
    val existingScheduleId: String? = null,
    val newSchedule: ClassSchedule? = null,
)

sealed interface CreateStudentGroupResult {
    data class Success(val group: StudentGroup) : CreateStudentGroupResult
    data object DuplicateName : CreateStudentGroupResult
    data object InvalidName : CreateStudentGroupResult
    data object InvalidSchedule : CreateStudentGroupResult
    data class ScheduleOverlap(val existingScheduleName: String) : CreateStudentGroupResult
}
