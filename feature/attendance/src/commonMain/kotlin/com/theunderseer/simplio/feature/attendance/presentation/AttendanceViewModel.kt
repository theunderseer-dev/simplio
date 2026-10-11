package com.theunderseer.simplio.feature.attendance.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.StudentGroup
import com.theunderseer.simplio.feature.attendance.domain.result.MarkStudentPresentResult
import com.theunderseer.simplio.feature.attendance.domain.result.RemoveStudentAttendanceResult
import com.theunderseer.simplio.feature.attendance.domain.usecase.DeleteStudentUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.GetAttendanceUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.GetPackageTypesUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.GetStudentGroupsUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.MarkStudentPresentUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.RegisterStudentUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.RemoveStudentAttendanceUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.time.Duration.Companion.seconds

class AttendanceViewModel(
    private val getAttendanceUseCase: GetAttendanceUseCase,
    private val getPackageTypesUseCase: GetPackageTypesUseCase,
    private val getStudentGroupsUseCase: GetStudentGroupsUseCase,
    private val registerStudentUseCase: RegisterStudentUseCase,
    private val markStudentPresentUseCase: MarkStudentPresentUseCase,
    private val removeStudentAttendanceUseCase: RemoveStudentAttendanceUseCase,
    private val deleteStudentUseCase: DeleteStudentUseCase,
    currentDates: Flow<LocalDate>,
) : ViewModel() {
    private val attendanceData =
        MutableStateFlow<AttendanceDataState>(
            AttendanceDataState.Loading,
        )

    private val markAttendanceState =
        MutableStateFlow<MarkStudentPresentUiState>(
            MarkStudentPresentUiState.Idle,
        )

    private val removeAttendanceState =
        MutableStateFlow<RemoveStudentAttendanceUiState>(
            RemoveStudentAttendanceUiState.Idle,
        )

    val uiState: StateFlow<AttendanceUiState> =
        combine(
            attendanceData,
            currentDates,
            markAttendanceState,
            removeAttendanceState,
        ) { data, currentDate, markState, removeState ->
            when (data) {
                AttendanceDataState.Loading ->
                    AttendanceUiState.Loading

                is AttendanceDataState.Content ->
                    attendanceContentUiState(
                        attendance = data.attendance,
                        packageTypes = data.packageTypes,
                        currentDate = currentDate,
                    ).copy(
                        studentGroups = data.studentGroups,
                        markStudentPresentState = markState,
                        removeStudentAttendanceState = removeState,
                    )

                is AttendanceDataState.Error ->
                    AttendanceUiState.Error(
                        message = data.message,
                    )
            }
        }.stateIn(
            scope = viewModelScope,
            started =
            SharingStarted.WhileSubscribed(
                stopTimeout = 5.seconds,
            ),
            initialValue = AttendanceUiState.Loading,
        )

    init {
        loadAttendance()
    }

    fun registerStudent(
        fullName: String,
        groupId: Int,
        packageType: PackageType,
        registeredOn: LocalDate,
        visitCount: Int = 1,
    ) {
        viewModelScope.launch {
            try {
                registerStudentUseCase(
                    fullName = fullName,
                    groupId = groupId,
                    packageType = packageType,
                    registeredOn = registeredOn,
                    visitCount = visitCount,
                )

                refreshAttendance()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                attendanceData.value =
                    AttendanceDataState.Error(
                        message =
                        exception.message
                            ?: "Failed to register student",
                    )
            }
        }
    }

    fun markStudentPresent(studentId: Int, attendanceDate: LocalDate, visitCount: Int = 1) {
        if (markAttendanceState.value == MarkStudentPresentUiState.Saving) {
            return
        }

        markAttendanceState.value = MarkStudentPresentUiState.Saving

        viewModelScope.launch {
            try {
                val result =
                    markStudentPresentUseCase(
                        studentId = studentId,
                        attendanceDate = attendanceDate,
                        visitCount = visitCount,
                    )

                val errorMessage =
                    when (result) {
                        is MarkStudentPresentResult.Success -> {
                            refreshAttendance()
                            markAttendanceState.value = MarkStudentPresentUiState.Success
                            return@launch
                        }

                        is MarkStudentPresentResult.PackageCompleted ->
                            "Student package is completed"

                        is MarkStudentPresentResult.PackageExpired ->
                            "Student package has expired"

                        MarkStudentPresentResult.PackageNotFound ->
                            "Student package for student $studentId not found"

                        MarkStudentPresentResult.InvalidVisitCount ->
                            "Visit count is not allowed for this package"

                        MarkStudentPresentResult.InsufficientRemainingVisits ->
                            "Not enough visits remaining"
                    }

                markAttendanceState.value =
                    MarkStudentPresentUiState.Error(
                        message = errorMessage,
                    )
            } catch (exception: CancellationException) {
                markAttendanceState.value = MarkStudentPresentUiState.Idle
                throw exception
            } catch (exception: Exception) {
                markAttendanceState.value =
                    MarkStudentPresentUiState.Error(
                        message =
                        exception.message
                            ?: "Failed to mark student present",
                    )
            }
        }
    }

    fun removeStudentAttendance(studentId: Int, attendanceDate: LocalDate) {
        if (removeAttendanceState.value == RemoveStudentAttendanceUiState.Saving) {
            return
        }

        removeAttendanceState.value = RemoveStudentAttendanceUiState.Saving

        viewModelScope.launch {
            try {
                val result =
                    removeStudentAttendanceUseCase(
                        studentId = studentId,
                        attendanceDate = attendanceDate,
                    )

                when (result) {
                    is RemoveStudentAttendanceResult.Success -> {
                        refreshAttendance()
                        removeAttendanceState.value =
                            RemoveStudentAttendanceUiState.Success
                    }

                    RemoveStudentAttendanceResult.FirstAttendanceCannotBeRemoved ->
                        removeAttendanceState.value =
                            RemoveStudentAttendanceUiState.Error(
                                message = "The first attendance cannot be removed",
                            )

                    RemoveStudentAttendanceResult.PackageNotFound ->
                        removeAttendanceState.value =
                            RemoveStudentAttendanceUiState.Error(
                                message = "Student package for student $studentId not found",
                            )
                }
            } catch (exception: CancellationException) {
                removeAttendanceState.value = RemoveStudentAttendanceUiState.Idle
                throw exception
            } catch (exception: Exception) {
                removeAttendanceState.value =
                    RemoveStudentAttendanceUiState.Error(
                        message =
                        exception.message
                            ?: "Failed to remove student attendance",
                    )
            }
        }
    }

    fun deleteStudent(studentId: Int) {
        viewModelScope.launch {
            try {
                deleteStudentUseCase(
                    studentId = studentId,
                )

                refreshAttendance()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                attendanceData.value =
                    AttendanceDataState.Error(
                        message = exception.message ?: "Failed to delete student",
                    )
            }
        }
    }

    fun clearMarkStudentPresentState() {
        if (markAttendanceState.value != MarkStudentPresentUiState.Saving) {
            markAttendanceState.value = MarkStudentPresentUiState.Idle
        }
    }

    fun clearRemoveStudentAttendanceState() {
        if (removeAttendanceState.value != RemoveStudentAttendanceUiState.Saving) {
            removeAttendanceState.value = RemoveStudentAttendanceUiState.Idle
        }
    }

    private fun loadAttendance() {
        viewModelScope.launch {
            try {
                refreshAttendance()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                attendanceData.value =
                    AttendanceDataState.Error(
                        message =
                        exception.message
                            ?: "Failed to load attendance",
                    )
            }
        }
    }

    private suspend fun refreshAttendance() {
        attendanceData.value =
            AttendanceDataState.Content(
                attendance = getAttendanceUseCase(),
                packageTypes = getPackageTypesUseCase(),
                studentGroups = getStudentGroupsUseCase(),
            )
    }

    private sealed interface AttendanceDataState {
        data object Loading : AttendanceDataState

        data class Content(
            val attendance: Attendance,
            val packageTypes: List<PackageType>,
            val studentGroups: List<StudentGroup>,
        ) : AttendanceDataState

        data class Error(val message: String) : AttendanceDataState
    }
}
