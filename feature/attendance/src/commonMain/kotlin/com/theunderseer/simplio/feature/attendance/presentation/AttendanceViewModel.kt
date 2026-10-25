package com.theunderseer.simplio.feature.attendance.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.ClassSchedule
import com.theunderseer.simplio.feature.attendance.domain.model.CreatePackageTypeRequest
import com.theunderseer.simplio.feature.attendance.domain.model.CreatePackageTypeResult
import com.theunderseer.simplio.feature.attendance.domain.model.CreateStudentGroupRequest
import com.theunderseer.simplio.feature.attendance.domain.model.CreateStudentGroupResult
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.StudentGroup
import com.theunderseer.simplio.feature.attendance.domain.repository.ClassScheduleRepository
import com.theunderseer.simplio.feature.attendance.domain.result.MarkStudentPresentResult
import com.theunderseer.simplio.feature.attendance.domain.result.RemoveStudentAttendanceResult
import com.theunderseer.simplio.feature.attendance.domain.result.RenewStudentPackageResult
import com.theunderseer.simplio.feature.attendance.domain.schedule.DefaultClassSchedule
import com.theunderseer.simplio.feature.attendance.domain.schedule.ScheduleOverlap
import com.theunderseer.simplio.feature.attendance.domain.schedule.findScheduleOverlaps
import com.theunderseer.simplio.feature.attendance.domain.transaction.TransactionRunner
import com.theunderseer.simplio.feature.attendance.domain.usecase.CreatePackageTypeUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.CreateStudentGroupUseCase
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class AttendanceViewModel(
    private val getAttendanceUseCase: GetAttendanceUseCase,
    private val getPackageTypesUseCase: GetPackageTypesUseCase,
    private val createPackageTypeUseCase: CreatePackageTypeUseCase,
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
    private val createStudentGroupUseCase: CreateStudentGroupUseCase? = null,
    private val classScheduleRepository: ClassScheduleRepository? = null,
    currentInstants: Flow<Instant?> = flowOf(null),
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

    private val currentInstant = currentInstants.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val packageCreationState = MutableStateFlow<CreatePackageTypeUiState>(CreatePackageTypeUiState.Idle)

    private val groupCreationState = MutableStateFlow<CreateStudentGroupUiState>(CreateStudentGroupUiState.Idle)

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
                        groupSchedules = data.groupSchedules,
                    ).copy(
                        studentGroups = data.studentGroups,
                        classSchedules = data.classSchedules,
                        groupSchedules = data.groupSchedules,
                        scheduleOverlaps = data.scheduleOverlaps,
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
        }.combine(groupCreationState) { state, creation ->
            if (state is AttendanceUiState.Content) state.copy(createStudentGroupState = creation) else state
        }.combine(packageCreationState) { state, creation ->
            if (state is AttendanceUiState.Content) state.copy(createPackageTypeState = creation) else state
        }.combine(currentInstant) { state, now ->
            if (state is AttendanceUiState.Content && now != null) {
                val dates = state.studentGroups.associate { group ->
                    val zone = state.groupSchedules[group.id]?.firstOrNull()?.timeZoneId
                        ?: DefaultClassSchedule.schedule.timeZoneId
                    group.id to now.toLocalDateTime(TimeZone.of(zone)).date
                }
                state.copy(
                    groupCurrentDates = dates,
                    rows = attendanceContentUiState(
                        state.attendance,
                        state.packageTypes,
                        state.currentDate,
                        state.groupSchedules,
                        dates,
                    ).rows,
                )
            } else {
                state
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
            combine(attendanceData, currentInstant) { data, now ->
                if (data !is AttendanceDataState.Content || now == null) {
                    null
                } else {
                    data.classSchedules.map {
                        it.id to now.toLocalDateTime(TimeZone.of(it.timeZoneId)).date
                    }
                }
            }.distinctUntilChangedBy { it }.collect { if (it != null) loadClassHistory() }
        }

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
                .distinctUntilChangedBy { it }
                .collect { currentDate -> loadClassHistory(currentDate = currentDate) }
        }
    }

    fun createPackageType(request: CreatePackageTypeRequest) {
        if (packageCreationState.value == CreatePackageTypeUiState.Saving) return
        packageCreationState.value = CreatePackageTypeUiState.Saving
        viewModelScope.launch {
            try {
                when (val result = createPackageTypeUseCase(request)) {
                    is CreatePackageTypeResult.Success -> {
                        refreshAttendance()
                        packageCreationState.value = CreatePackageTypeUiState.Success(result.packageType.name)
                    }
                    CreatePackageTypeResult.DuplicateName -> {
                        refreshAttendance()
                        packageCreationState.value = CreatePackageTypeUiState.DuplicateName
                    }
                    is CreatePackageTypeResult.Invalid ->
                        packageCreationState.value = CreatePackageTypeUiState.Invalid(result.reason)
                }
            } catch (error: CancellationException) {
                packageCreationState.value = CreatePackageTypeUiState.Idle
                throw error
            } catch (error: Exception) {
                packageCreationState.value = CreatePackageTypeUiState.SaveFailed
            }
        }
    }

    fun clearCreatePackageTypeState() {
        if (packageCreationState.value != CreatePackageTypeUiState.Saving) {
            packageCreationState.value = CreatePackageTypeUiState.Idle
        }
    }

    fun createStudentGroup(request: CreateStudentGroupRequest) {
        if (groupCreationState.value == CreateStudentGroupUiState.Saving) return
        groupCreationState.value = CreateStudentGroupUiState.Saving
        viewModelScope.launch {
            try {
                when (val result = checkNotNull(createStudentGroupUseCase)(request)) {
                    is CreateStudentGroupResult.Success -> {
                        refreshAttendance()
                        loadClassHistory()
                        groupCreationState.value = CreateStudentGroupUiState.Success(result.group.name)
                    }
                    CreateStudentGroupResult.DuplicateName -> {
                        refreshAttendance()
                        groupCreationState.value =
                            CreateStudentGroupUiState.Error("A group with this name already exists")
                    }
                    CreateStudentGroupResult.InvalidName ->
                        groupCreationState.value = CreateStudentGroupUiState.Error("Enter a valid group name")
                    CreateStudentGroupResult.InvalidSchedule ->
                        groupCreationState.value = CreateStudentGroupUiState.Error("Choose a valid weekly schedule")
                    is CreateStudentGroupResult.ScheduleOverlap -> {
                        refreshAttendance()
                        groupCreationState.value = CreateStudentGroupUiState.Error(
                            "Schedule overlaps with \"${result.existingScheduleName}\". " +
                                "Use the existing schedule or change the time or days",
                        )
                    }
                }
            } catch (error: CancellationException) {
                groupCreationState.value = CreateStudentGroupUiState.Idle
                throw error
            } catch (error: Exception) {
                groupCreationState.value = CreateStudentGroupUiState.Error("Couldn't save the group. Try again.")
            }
        }
    }

    fun clearCreateStudentGroupState() {
        if (groupCreationState.value != CreateStudentGroupUiState.Saving) {
            groupCreationState.value = CreateStudentGroupUiState.Idle
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
                    RenewStudentPackageResult.AttendanceAlreadyRecorded -> "Attendance already recorded for this date"
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
                        MarkStudentPresentResult.AttendanceAlreadyRecorded ->
                            "Attendance already recorded for this date"

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
                    getClassHistoryUseCase(
                        currentDate = date,
                        beforeDate = beforeDate,
                        currentInstant = currentInstant.value,
                    )
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
                    val page = transactionRunner.run {
                        getClassHistoryUseCase(currentDate = date, currentInstant = currentInstant.value)
                    }

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
                val schedules = classScheduleRepository?.getAll() ?: listOf(DefaultClassSchedule.schedule)
                val groups = getStudentGroupsUseCase()
                val byGroup = groups.mapNotNull { group ->
                    val linked = classScheduleRepository?.getForGroup(group.id)
                    if (linked != null && linked.any { it.id != DefaultClassSchedule.ID }) group.id to linked else null
                }.toMap()
                AttendanceDataState.Content(
                    attendance = getAttendanceUseCase(),
                    packageTypes = getPackageTypesUseCase(),
                    studentGroups = groups,
                    classSchedules = schedules,
                    groupSchedules = byGroup,
                    scheduleOverlaps = findScheduleOverlaps(schedules),
                )
            }
    }

    private sealed interface AttendanceDataState {
        data object Loading : AttendanceDataState

        data class Content(
            val attendance: Attendance,
            val packageTypes: List<PackageType>,
            val studentGroups: List<StudentGroup>,
            val classSchedules: List<ClassSchedule>,
            val groupSchedules: Map<Int, List<ClassSchedule>>,
            val scheduleOverlaps: List<ScheduleOverlap>,
        ) : AttendanceDataState

        data class Error(val message: String) : AttendanceDataState
    }
}
