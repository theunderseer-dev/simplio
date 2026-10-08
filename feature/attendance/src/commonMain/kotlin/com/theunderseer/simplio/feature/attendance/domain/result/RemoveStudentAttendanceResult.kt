package com.theunderseer.simplio.feature.attendance.domain.result

import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage

sealed interface RemoveStudentAttendanceResult {
    data class Success(val studentPackage: StudentPackage) : RemoveStudentAttendanceResult

    data object PackageNotFound : RemoveStudentAttendanceResult

    data object FirstAttendanceCannotBeRemoved : RemoveStudentAttendanceResult
}
