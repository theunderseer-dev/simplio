package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import com.theunderseer.simplio.feature.attendance.domain.model.ClassAttendanceProblem
import com.theunderseer.simplio.feature.attendance.domain.model.CreatePackageTypeRequest
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent
import com.theunderseer.simplio.feature.attendance.resources.Res
import com.theunderseer.simplio.feature.attendance.resources.class_draft_stale
import com.theunderseer.simplio.feature.attendance.resources.class_invalid_selection
import com.theunderseer.simplio.feature.attendance.resources.class_save_failed
import com.theunderseer.simplio.feature.attendance.resources.class_unavailable
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ClassAttendanceWorkflow(
    state: AttendanceUiState.Content,
    callbacks: ClassAttendanceCallbacks,
    onCreatePackage: (CreatePackageTypeRequest) -> Unit,
    onClearPackageError: () -> Unit,
) {
    val content = state.classAttendanceState as? ClassAttendanceUiState.Content
    val action = content?.studentAction
    if (content == null || action == null) {
        ClassAttendanceDialog(
            state.classAttendanceState,
            callbacks.close,
            callbacks.open,
            callbacks.save,
            callbacks.select,
            callbacks.addStudent,
            callbacks.renewStudent,
        )
        return
    }
    val sheet = content.sheet
    val groups = state.studentGroups.filter { it.id in sheet.groupIds }
    val error = if (action.problem == null && !action.saveFailed) {
        null
    } else {
        stringResource(
            when (action.problem) {
                ClassAttendanceProblem.STALE -> Res.string.class_draft_stale
                ClassAttendanceProblem.INVALID_SELECTION -> Res.string.class_invalid_selection
                ClassAttendanceProblem.UNAVAILABLE, ClassAttendanceProblem.CANCELLED -> Res.string.class_unavailable
                else -> Res.string.class_save_failed
            },
        )
    }
    key(sheet.session.id, action.kind, action.studentId) {
        when (action.kind) {
            ClassStudentActionKind.REGISTER -> StudentRegistrationDialog(
                studentGroups = groups,
                packageTypes = state.packageTypes,
                initialDate = sheet.session.date,
                fixedDate = sheet.session.date,
                initialGroupId = groups.singleOrNull()?.id,
                groupSchedules = state.groupSchedules,
                isSaving = action.isSaving,
                errorMessage = error,
                visible = !action.creatingPackage,
                preferredPackageTypeId = action.preferredPackageTypeId,
                onCreatePackage = { callbacks.showPackageCreation(true) },
                onRegister = { name, group, pack, _, visits, record, note ->
                    callbacks.register(name, group, pack, visits, record, note)
                },
                onDismiss = callbacks.dismissStudentAction,
            )
            ClassStudentActionKind.RENEW -> {
                val row = sheet.rows.single { it.student.id == action.studentId }
                StudentPackageRenewalDialog(
                    registeredStudent = RegisteredStudent(row.student, checkNotNull(row.studentPackage)),
                    studentGroups = groups,
                    packageTypes = state.packageTypes,
                    currentDate = sheet.session.date,
                    fixedDate = sheet.session.date,
                    groupSchedules = state.groupSchedules,
                    isSaving = action.isSaving,
                    errorMessage = error,
                    visible = !action.creatingPackage,
                    preferredPackageTypeId = action.preferredPackageTypeId,
                    onCreatePackage = { callbacks.showPackageCreation(true) },
                    onRenew = { pack, _, visits, record, note -> callbacks.renew(pack, visits, record, note) },
                    onDismiss = callbacks.dismissStudentAction,
                )
            }
        }
        if (action.creatingPackage) {
            PackageTypeCreationDialog(
                packageTypes = state.packageTypes,
                state = state.createPackageTypeState,
                onConfirm = onCreatePackage,
                onClearError = onClearPackageError,
                onDismiss = { callbacks.showPackageCreation(false) },
            )
        }
    }
}
