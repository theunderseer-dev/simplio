package com.theunderseer.simplio.feature.attendance.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.result.RemoveStudentAttendanceResult
import com.theunderseer.simplio.feature.attendance.domain.usecase.DeleteStudentUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.GetAttendanceUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.GetPackageTypesUseCase
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

    val uiState: StateFlow<AttendanceUiState> =
        combine(
            attendanceData,
            currentDates,
        ) { data, currentDate ->
            when (data) {
                AttendanceDataState.Loading ->
                    AttendanceUiState.Loading

                is AttendanceDataState.Content ->
                    attendanceContentUiState(
                        attendance = data.attendance,
                        packageTypes = data.packageTypes,
                        currentDate = currentDate,
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

    fun registerStudent(fullName: String, packageType: PackageType, registeredOn: LocalDate) {
        viewModelScope.launch {
            try {
                registerStudentUseCase(
                    fullName = fullName,
                    packageType = packageType,
                    registeredOn = registeredOn,
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

    fun markStudentPresent(studentId: Int, attendanceDate: LocalDate) {
        viewModelScope.launch {
            try {
                markStudentPresentUseCase(
                    studentId = studentId,
                    attendanceDate = attendanceDate,
                )

                refreshAttendance()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                attendanceData.value =
                    AttendanceDataState.Error(
                        message =
                        exception.message
                            ?: "Failed to mark student present",
                    )
            }
        }
    }

    fun removeStudentAttendance(studentId: Int, attendanceDate: LocalDate) {
        viewModelScope.launch {
            try {
                val result =
                    removeStudentAttendanceUseCase(
                        studentId = studentId,
                        attendanceDate = attendanceDate,
                    )

                when (result) {
                    is RemoveStudentAttendanceResult.Success ->
                        refreshAttendance()

                    RemoveStudentAttendanceResult.FirstAttendanceCannotBeRemoved ->
                        attendanceData.value =
                            AttendanceDataState.Error(
                                message = "The first attendance cannot be removed",
                            )

                    RemoveStudentAttendanceResult.PackageNotFound ->
                        attendanceData.value =
                            AttendanceDataState.Error(
                                message = "Student package for student $studentId not found",
                            )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                attendanceData.value =
                    AttendanceDataState.Error(
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
            )
    }

    private sealed interface AttendanceDataState {
        data object Loading : AttendanceDataState

        data class Content(val attendance: Attendance, val packageTypes: List<PackageType>) : AttendanceDataState

        data class Error(val message: String) : AttendanceDataState
    }
}
