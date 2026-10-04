package com.theunderseer.simplio.feature.attendance.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.usecase.GetAttendanceUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.GetPackageTypesUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.RegisterStudentUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

class AttendanceViewModel(
    private val getAttendanceUseCase: GetAttendanceUseCase,
    private val getPackageTypesUseCase: GetPackageTypesUseCase,
    private val registerStudentUseCase: RegisterStudentUseCase,
    private val currentDate: () -> LocalDate,
) : ViewModel() {
    private val _uiState =
        MutableStateFlow<AttendanceUiState>(
            AttendanceUiState.Loading,
        )

    val uiState: StateFlow<AttendanceUiState> =
        _uiState.asStateFlow()

    init {
        loadAttendance()
    }

    fun registerStudent(fullName: String, packageType: PackageType) {
        viewModelScope.launch {
            try {
                registerStudentUseCase(
                    fullName = fullName,
                    packageType = packageType,
                    registeredOn = currentDate(),
                )

                _uiState.value =
                    AttendanceUiState.Content(
                        attendance = getAttendanceUseCase(),
                        packageTypes = getPackageTypesUseCase(),
                    )
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _uiState.value =
                    AttendanceUiState.Error(
                        message =
                        exception.message
                            ?: "Failed to register student",
                    )
            }
        }
    }

    private fun loadAttendance() {
        viewModelScope.launch {
            try {
                _uiState.value =
                    AttendanceUiState.Content(
                        attendance = getAttendanceUseCase(),
                        packageTypes = getPackageTypesUseCase(),
                    )
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _uiState.value =
                    AttendanceUiState.Error(
                        message =
                        exception.message
                            ?: "Failed to load attendance",
                    )
            }
        }
    }
}
