package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock

@Composable
fun AttendanceRoute(
    onCopyAttendance: (String) -> Unit,
    viewModel: AttendanceViewModel = koinViewModel(),
    reminderSessionId: String? = null,
    onReminderHandled: () -> Unit = {},
    additionalOptions: (@Composable (dismiss: () -> Unit) -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsState()

    AttendanceScreen(
        state = state,
        reminderSessionId = reminderSessionId,
        additionalOptions = additionalOptions,
        onOpenReminder = { sessionId ->
            if (viewModel.openClassAttendanceFromReminder(sessionId, Clock.System.now())) {
                onReminderHandled()
            }
        },
        classAttendanceCallbacks = ClassAttendanceCallbacks(
            viewModel::openClassAttendance, viewModel::closeClassAttendance, viewModel::saveClassAttendance,
            viewModel::selectClassAttendance, viewModel::addStudentToClass, viewModel::renewStudentInClass,
            viewModel::dismissClassStudentAction, viewModel::showClassPackageCreation,
            viewModel::registerStudentInClass, viewModel::renewPackageInClass,
        ),
        onRegisterStudent = viewModel::registerStudent,
        onClearRegisterStudentState = viewModel::clearRegisterStudentState,
        onDeleteStudentGroup = viewModel::deleteStudentGroup,
        onClearDeleteStudentGroupState = viewModel::clearDeleteStudentGroupState,
        onCreatePackageType = viewModel::createPackageType,
        onClearCreatePackageTypeState = viewModel::clearCreatePackageTypeState,
        onDeletePackageType = viewModel::deletePackageType,
        onClearDeletePackageTypeState = viewModel::clearDeletePackageTypeState,
        onCreateStudentGroup = viewModel::createStudentGroup,
        onClearCreateStudentGroupState = viewModel::clearCreateStudentGroupState,
        onRenewStudentPackage = viewModel::renewStudentPackage,
        onClearRenewStudentPackageState = viewModel::clearRenewStudentPackageState,
        onDeleteStudent = viewModel::deleteStudent,
        onCopyAttendance = onCopyAttendance,
        onMarkStudentPresent = viewModel::markStudentPresent,
        onRemoveStudentAttendance = viewModel::removeStudentAttendance,
        onClearMarkStudentPresentState = viewModel::clearMarkStudentPresentState,
        onClearRemoveStudentAttendanceState = viewModel::clearRemoveStudentAttendanceState,
        onLoadNextClassHistoryPage = viewModel::loadNextClassHistoryPage,
        onRetryClassHistory = viewModel::retryClassHistory,
        onUpdateStudentNote = viewModel::updateStudentNote,
        onClearUpdateStudentNoteState = viewModel::clearUpdateStudentNoteState,
    )
}
