package com.theunderseer.simplio.feature.attendance.presentation

import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.ClassSchedule
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent
import com.theunderseer.simplio.feature.attendance.domain.model.StudentGroup
import com.theunderseer.simplio.feature.attendance.domain.schedule.AttendanceCheckState
import com.theunderseer.simplio.feature.attendance.domain.schedule.DefaultClassSchedule
import com.theunderseer.simplio.feature.attendance.domain.schedule.ScheduleOverlap
import kotlinx.datetime.LocalDate

sealed interface AttendanceUiState {
    data object Loading : AttendanceUiState

    data class Content(
        val attendance: Attendance,
        val studentGroups: List<StudentGroup> = emptyList(),
        val packageTypes: List<PackageType>,
        val currentDate: LocalDate,
        val latestClassDate: LocalDate,
        val rows: List<StudentAttendanceUiState>,
        val markStudentPresentState: MarkStudentPresentUiState = MarkStudentPresentUiState.Idle,
        val removeStudentAttendanceState: RemoveStudentAttendanceUiState = RemoveStudentAttendanceUiState.Idle,
        val classHistoryState: ClassHistoryUiState = ClassHistoryUiState.Loading,
        val renewStudentPackageState: RenewStudentPackageUiState = RenewStudentPackageUiState.Idle,
        val updateStudentNoteState: UpdateStudentNoteUiState = UpdateStudentNoteUiState.Idle,
        val classSchedules: List<ClassSchedule> = listOf(DefaultClassSchedule.schedule),
        val groupSchedules: Map<Int, List<ClassSchedule>> = emptyMap(),
        val groupCurrentDates: Map<Int, LocalDate> = emptyMap(),
        val createStudentGroupState: CreateStudentGroupUiState = CreateStudentGroupUiState.Idle,
        val scheduleOverlaps: List<ScheduleOverlap> = emptyList(),
        val createPackageTypeState: CreatePackageTypeUiState = CreatePackageTypeUiState.Idle,
        val classAttendanceState: ClassAttendanceUiState = ClassAttendanceUiState.Closed,
    ) : AttendanceUiState

    data class Error(val message: String) : AttendanceUiState
}

data class StudentAttendanceUiState(
    val registeredStudent: RegisteredStudent,
    val checkState: AttendanceCheckState,
    val isCheckEnabled: Boolean,
    val requiresRenewal: Boolean = false,
    val effectiveClassDate: LocalDate? = null,
)
