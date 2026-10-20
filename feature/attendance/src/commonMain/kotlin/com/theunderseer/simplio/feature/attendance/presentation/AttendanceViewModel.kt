package com.theunderseer.simplio.feature.attendance.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.StudentGroup
import com.theunderseer.simplio.feature.attendance.domain.result.MarkStudentPresentResult
import com.theunderseer.simplio.feature.attendance.domain.result.RemoveStudentAttendanceResult
import com.theunderseer.simplio.feature.attendance.domain.result.RenewStudentPackageResult
import com.theunderseer.simplio.feature.attendance.domain.schedule.latestScheduledClassDate
import com.theunderseer.simplio.feature.attendance.domain.transaction.TransactionRunner
import com.theunderseer.simplio.feature.attendance.domain.usecase.DeleteStudentUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.GetAttendanceUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.GetClassHistoryUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.GetPackageTypesUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.GetStudentGroupsUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.MarkStudentPresentUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.RegisterStudentUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.RemoveStudentAttendanceUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.RenewStudentPackageUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.UpdateStudentNoteUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
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
    private val getClassHistoryUseCase: GetClassHistoryUseCase,
    private val renewStudentPackageUseCase: RenewStudentPackageUseCase,
    private val updateStudentNoteUseCase: UpdateStudentNoteUseCase,
    private val currentDates: Flow<LocalDate>,
    private val transactionRunner: TransactionRunner,
    private val localChanges: Flow<Unit>,
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

    private val classHistoryData =
        MutableStateFlow<ClassHistoryUiState>(
            ClassHistoryUiState.Loading,
        )

    private val updateStudentNoteState =
        MutableStateFlow<UpdateStudentNoteUiState>(UpdateStudentNoteUiState.Idle)

    private val renewalState = MutableStateFlow<RenewStudentPackageUiState>(RenewStudentPackageUiState.Idle)

    private var classHistoryJob: Job? = null

    val uiState: StateFlow<AttendanceUiState> =
        combine(
            attendanceData,
            currentDates,
            markAttendanceState,
            removeAttendanceState,
            classHistoryData,
        ) { data, currentDate, markState, removeState, historyState ->
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
                        classHistoryState = historyState,
                    )

                is AttendanceDataState.Error ->
                    AttendanceUiState.Error(
                        message = data.message,
                    )
            }
        }.combine(updateStudentNoteState) { state, noteState ->
            when (state) {
                is AttendanceUiState.Content -> state.copy(updateStudentNoteState = noteState)
                else -> state
            }
        }.combine(renewalState) { state, renewal ->
            when (state) {
                is AttendanceUiState.Content -> state.copy(renewStudentPackageState = renewal)
                else -> state
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
        viewModelScope.launch {
            localChanges.collect {
                try {
                    refreshAttendance()
                    loadClassHistory()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    attendanceData.value = AttendanceDataState.Error(error.message ?: "Failed to load attendance")
                }
            }
        }

        viewModelScope.launch {
            currentDates
                .distinctUntilChangedBy { latestScheduledClassDate(today = it) }
                .collect { currentDate -> loadClassHistory(currentDate = currentDate) }
        }
    }

    fun registerStudent(
        fullName: String,
        groupId: Int,
        packageType: PackageType,
        registeredOn: LocalDate,
        visitCount: Int = 1,
        recordFirstAttendance: Boolean = true,
        note: String = "",
    ) {
        viewModelScope.launch {
            try {
                registerStudentUseCase(
                    fullName = fullName,
                    groupId = groupId,
                    packageType = packageType,
                    registeredOn = registeredOn,
                    visitCount = visitCount,
                    recordFirstAttendance = recordFirstAttendance,
                    note = note,
                )

                refreshAttendance()
                loadClassHistory()
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

    fun renewStudentPackage(
        studentId: Int,
        previousPackageId: Int,
        packageType: PackageType,
        renewedOn: LocalDate,
        visitCount: Int,
        recordFirstAttendance: Boolean,
        note: String,
    ) {
        if (renewalState.value is RenewStudentPackageUiState.Saving) return
        renewalState.value = RenewStudentPackageUiState.Saving(studentId)
        viewModelScope.launch {
            try {
                val result = renewStudentPackageUseCase(
                    studentId = studentId,
                    previousPackageId = previousPackageId,
                    packageType = packageType,
                    renewedOn = renewedOn,
                    visitCount = visitCount,
                    recordFirstAttendance = recordFirstAttendance,
                    note = note,
                )
                val message = when (result) {
                    is RenewStudentPackageResult.Success -> {
                        refreshAttendance()
                        loadClassHistory()
                        renewalState.value = RenewStudentPackageUiState.Success(studentId)
                        return@launch
                    }
                    is RenewStudentPackageResult.ActivePackageExists -> "Student package is still active"
                    RenewStudentPackageResult.PackageNotFound -> "Student package not found"
                    RenewStudentPackageResult.StudentNotFound -> "Student not found"
                    RenewStudentPackageResult.PackageChanged -> {
                        refreshAttendance()
                        "Package changed. Close this dialog and review it again."
                    }
                    RenewStudentPackageResult.InvalidVisitCount -> "Visit count is not allowed for this package"
                    is RenewStudentPackageResult.RenewalDateTooEarly ->
                        "Purchase cannot be before ${result.earliestAllowedDate.toDisplayDate()}"
                }
                renewalState.value = RenewStudentPackageUiState.Error(studentId, message)
            } catch (error: CancellationException) {
                renewalState.value = RenewStudentPackageUiState.Idle
                throw error
            } catch (error: Exception) {
                renewalState.value = RenewStudentPackageUiState.Error(studentId, "Failed to renew package. Try again.")
            }
        }
    }

    fun clearRenewStudentPackageState() {
        if (renewalState.value !is RenewStudentPackageUiState.Saving) {
            renewalState.value = RenewStudentPackageUiState.Idle
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
                            loadClassHistory()
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

                        is MarkStudentPresentResult.AttendanceDateTooEarly ->
                            "Attendance cannot be before ${result.earliestAllowedDate.toDisplayDate()}"
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
                        loadClassHistory()
                        removeAttendanceState.value =
                            RemoveStudentAttendanceUiState.Success
                    }

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

    fun updateStudentNote(studentId: Int, note: String) {
        if (updateStudentNoteState.value is UpdateStudentNoteUiState.Saving) {
            return
        }

        updateStudentNoteState.value = UpdateStudentNoteUiState.Saving(studentId)

        viewModelScope.launch {
            try {
                val updated = updateStudentNoteUseCase(studentId = studentId, note = note)

                if (!updated) {
                    updateStudentNoteState.value = UpdateStudentNoteUiState.Error(
                        studentId = studentId,
                        message = "Student not found",
                    )
                    return@launch
                }

                refreshAttendance()
                updateStudentNoteState.value = UpdateStudentNoteUiState.Success(studentId)
            } catch (exception: CancellationException) {
                updateStudentNoteState.value = UpdateStudentNoteUiState.Idle
                throw exception
            } catch (exception: Exception) {
                updateStudentNoteState.value = UpdateStudentNoteUiState.Error(
                    studentId = studentId,
                    message = exception.message ?: "Failed to save note",
                )
            }
        }
    }

    fun clearUpdateStudentNoteState() {
        if (updateStudentNoteState.value !is UpdateStudentNoteUiState.Saving) {
            updateStudentNoteState.value = UpdateStudentNoteUiState.Idle
        }
    }

    fun deleteStudent(studentId: Int) {
        viewModelScope.launch {
            try {
                deleteStudentUseCase(
                    studentId = studentId,
                )

                refreshAttendance()
                loadClassHistory()
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

    fun retryClassHistory() {
        when (val state = classHistoryData.value) {
            is ClassHistoryUiState.Error -> loadClassHistory()

            is ClassHistoryUiState.Content -> {
                if (state.nextPageErrorMessage != null) {
                    loadNextClassHistoryPage()
                }
            }

            ClassHistoryUiState.Loading -> Unit
        }
    }

    fun loadNextClassHistoryPage() {
        val content =
            classHistoryData.value as? ClassHistoryUiState.Content
                ?: return
        val beforeDate = content.nextBeforeDate ?: return

        if (content.isLoadingNextPage) {
            return
        }

        classHistoryData.value = content.copy(
            isLoadingNextPage = true,
            nextPageErrorMessage = null,
        )

        classHistoryJob = viewModelScope.launch {
            try {
                val date = currentDates.first()
                val page = transactionRunner.run {
                    getClassHistoryUseCase(currentDate = date, beforeDate = beforeDate)
                }

                currentCoroutineContext().ensureActive()

                classHistoryData.value = content.copy(
                    entries = content.entries + page.entries,
                    nextBeforeDate = page.nextBeforeDate,
                    isLoadingNextPage = false,
                    nextPageErrorMessage = null,
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                currentCoroutineContext().ensureActive()

                classHistoryData.value = content.copy(
                    isLoadingNextPage = false,
                    nextPageErrorMessage =
                    exception.message
                        ?: "Failed to load more class history",
                )
            }
        }
    }

    private fun loadClassHistory(currentDate: LocalDate? = null) {
        classHistoryJob?.cancel()
        classHistoryData.value = ClassHistoryUiState.Loading

        classHistoryJob =
            viewModelScope.launch {
                try {
                    val date = currentDate ?: currentDates.first()
                    val page = transactionRunner.run { getClassHistoryUseCase(currentDate = date) }

                    currentCoroutineContext().ensureActive()

                    classHistoryData.value =
                        ClassHistoryUiState.Content(
                            entries = page.entries,
                            nextBeforeDate = page.nextBeforeDate,
                        )
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    currentCoroutineContext().ensureActive()

                    classHistoryData.value =
                        ClassHistoryUiState.Error(
                            message =
                            exception.message
                                ?: "Failed to load class history",
                        )
                }
            }
    }

    private suspend fun refreshAttendance() {
        attendanceData.value =
            transactionRunner.run {
                AttendanceDataState.Content(
                    attendance = getAttendanceUseCase(),
                    packageTypes = getPackageTypesUseCase(),
                    studentGroups = getStudentGroupsUseCase(),
                )
            }
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
