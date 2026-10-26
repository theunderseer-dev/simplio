package com.theunderseer.simplio.feature.attendance.presentation.dialog

import androidx.compose.runtime.Composable
import com.theunderseer.simplio.feature.attendance.domain.model.ClassSchedule
import com.theunderseer.simplio.feature.attendance.domain.model.CreateStudentGroupRequest
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.presentation.CreateStudentGroupUiState
import com.theunderseer.simplio.feature.attendance.presentation.StudentGroupCreationDialog

@Composable
internal fun StudentGroupCreationDialogForTest(
    schedules: List<ClassSchedule> = emptyList(),
    onCreate: (CreateStudentGroupRequest) -> Unit = {},
) {
    StudentGroupCreationDialog(
        AttendanceTestData.studentGroups,
        schedules,
        AttendanceTestData.currentDate,
        CreateStudentGroupUiState.Idle,
        onCreate,
        {},
    )
}
