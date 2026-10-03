package com.theunderseer.simplio.feature.attendance.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theunderseer.simplio.feature.attendance.domain.usecase.GetAttendanceUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class AttendanceViewModel(private val getAttendance: GetAttendanceUseCase) : ViewModel() {
    private val _uiState =
        MutableStateFlow<AttendanceUiState>(
            AttendanceUiState.Loading,
        )

    val uiState: StateFlow<AttendanceUiState> =
        _uiState.asStateFlow()

    init {
        loadAttendance()
    }

    private fun loadAttendance() {
        viewModelScope.launch {
            try {
                _uiState.value =
                    AttendanceUiState.Content(
                        attendance = getAttendance(),
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
