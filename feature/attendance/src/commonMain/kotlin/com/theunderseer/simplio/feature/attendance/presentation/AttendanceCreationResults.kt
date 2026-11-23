package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.runtime.Composable
import com.theunderseer.simplio.feature.attendance.domain.model.ClassAttendanceFailure
import com.theunderseer.simplio.feature.attendance.domain.model.CreatePackageTypeResult
import com.theunderseer.simplio.feature.attendance.domain.model.CreateStudentGroupResult
import com.theunderseer.simplio.feature.attendance.domain.model.InvalidStudentName
import com.theunderseer.simplio.feature.attendance.resources.Res
import com.theunderseer.simplio.feature.attendance.resources.class_cancelled_error
import com.theunderseer.simplio.feature.attendance.resources.student_name_invalid
import com.theunderseer.simplio.feature.attendance.resources.student_registration_failed
import org.jetbrains.compose.resources.stringResource

internal fun CreatePackageTypeResult.toUiState(): CreatePackageTypeUiState = when (this) {
    is CreatePackageTypeResult.Success -> CreatePackageTypeUiState.Success(packageType.name)
    CreatePackageTypeResult.DuplicateName -> CreatePackageTypeUiState.DuplicateName
    is CreatePackageTypeResult.Invalid -> CreatePackageTypeUiState.Invalid(reason)
}

internal fun CreateStudentGroupResult.toUiState(): CreateStudentGroupUiState = when (this) {
    is CreateStudentGroupResult.Success -> CreateStudentGroupUiState.Success(group.name)
    CreateStudentGroupResult.DuplicateName -> CreateStudentGroupUiState.DuplicateName
    CreateStudentGroupResult.InvalidName -> CreateStudentGroupUiState.InvalidName
    CreateStudentGroupResult.InvalidSchedule -> CreateStudentGroupUiState.Error("Choose a valid weekly schedule")
    is CreateStudentGroupResult.ScheduleOverlap -> CreateStudentGroupUiState.Error(
        "Schedule overlaps with \"$existingScheduleName\". Use the existing schedule or change the time or days",
    )
}

internal fun Exception.toRegistrationError(): RegisterStudentUiState.Error = when (this) {
    is InvalidStudentName -> RegisterStudentUiState.Error.INVALID_NAME
    is ClassAttendanceFailure -> RegisterStudentUiState.Error.CLASS_CANCELLED
    else -> RegisterStudentUiState.Error.SAVE_FAILED
}

@Composable
internal fun RegisterStudentUiState.errorMessage(): String? = when (this) {
    RegisterStudentUiState.Error.INVALID_NAME -> stringResource(Res.string.student_name_invalid)
    RegisterStudentUiState.Error.CLASS_CANCELLED -> stringResource(Res.string.class_cancelled_error)
    RegisterStudentUiState.Error.SAVE_FAILED -> stringResource(Res.string.student_registration_failed)
    else -> null
}
