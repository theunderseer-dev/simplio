package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.theunderseer.simplio.feature.attendance.domain.model.AttendanceRoster
import com.theunderseer.simplio.feature.attendance.resources.Res
import com.theunderseer.simplio.feature.attendance.resources.package_added
import com.theunderseer.simplio.feature.attendance.resources.schedule_entry_added
import com.theunderseer.simplio.feature.attendance.resources.student_registered
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Instant

@Suppress("FunctionNaming")
@Composable
internal fun IosAttendanceCreationDialog(
    action: IosAttendanceQuickAction,
    roster: AttendanceRoster,
    now: Instant,
    creation: IosAttendanceCreation,
    onCreated: (StringResource, String) -> Unit,
    onDismiss: () -> Unit,
) {
    when (action) {
        IosAttendanceQuickAction.Student -> IosRegistrationDialog(roster, now, creation, onCreated, onDismiss)
        IosAttendanceQuickAction.Package -> {
            val submission = rememberCreationSubmission<CreatePackageTypeUiState>(
                idle = CreatePackageTypeUiState.Idle,
                saving = CreatePackageTypeUiState.Saving,
                savedName = { (it as? CreatePackageTypeUiState.Success)?.name },
                onCreated = { onCreated(Res.string.package_added, it) },
            )
            PackageTypeCreationDialog(
                packageTypes = roster.packageTypes,
                state = submission.state,
                onConfirm = { request -> submission.submit { creation.addPackage(request) } },
                onClearError = submission::clearError,
                onDismiss = onDismiss,
            )
        }
        IosAttendanceQuickAction.Schedule -> {
            val submission = rememberCreationSubmission<CreateStudentGroupUiState>(
                idle = CreateStudentGroupUiState.Idle,
                saving = CreateStudentGroupUiState.Saving,
                savedName = { (it as? CreateStudentGroupUiState.Success)?.name },
                onCreated = { onCreated(Res.string.schedule_entry_added, it) },
            )
            StudentGroupCreationDialog(
                groups = roster.studentGroups,
                schedules = roster.classSchedules,
                today = defaultAttendanceDate(now),
                state = submission.state,
                onCreate = { request -> submission.submit { creation.addSchedule(request) } },
                onDismiss = onDismiss,
            )
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun IosRegistrationDialog(
    roster: AttendanceRoster,
    now: Instant,
    creation: IosAttendanceCreation,
    onCreated: (StringResource, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val submission = rememberCreationSubmission<RegisterStudentUiState>(
        idle = RegisterStudentUiState.Idle,
        saving = RegisterStudentUiState.Saving,
        savedName = { (it as? RegisterStudentUiState.Success)?.name },
        onCreated = { onCreated(Res.string.student_registered, it) },
    )
    StudentRegistrationDialog(
        studentGroups = roster.studentGroups,
        packageTypes = roster.packageTypes,
        initialDate = defaultAttendanceDate(now),
        groupSchedules = roster.groupSchedules,
        groupCurrentDates = attendanceDatesForGroups(roster.studentGroups.map { it.id }, roster.groupSchedules, now),
        isSaving = submission.state == RegisterStudentUiState.Saving,
        errorMessage = submission.state.errorMessage(),
        onRegister = { name, groupId, packageType, date, visitCount, recordAttendance, note ->
            submission.submit {
                creation.addStudent(
                    IosStudentRegistration(name, groupId, packageType, date, visitCount, recordAttendance, note),
                )
            }
        },
        onDismiss = onDismiss,
    )
}

@Composable
private fun <State> rememberCreationSubmission(
    idle: State,
    saving: State,
    savedName: (State) -> String?,
    onCreated: (String) -> Unit,
): CreationSubmission<State> {
    val scope = rememberCoroutineScope()
    val currentOnCreated by rememberUpdatedState(onCreated)
    return remember(scope, idle, saving) {
        CreationSubmission(scope, idle, saving, savedName) { currentOnCreated(it) }
    }
}

private class CreationSubmission<State>(
    private val scope: CoroutineScope,
    private val idle: State,
    private val saving: State,
    private val savedName: (State) -> String?,
    private val onCreated: (String) -> Unit,
) {
    var state by mutableStateOf(idle)
        private set

    fun submit(save: suspend () -> State) {
        if (state == saving) return
        state = saving
        scope.launch {
            val result = save()
            val name = savedName(result)
            if (name == null) state = result else onCreated(name)
        }
    }

    fun clearError() {
        if (state != saving) state = idle
    }
}
