package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent
import com.theunderseer.simplio.feature.attendance.domain.schedule.AttendanceCheckState
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

@Composable
fun AttendanceScreen(
    state: AttendanceUiState,
    onRegisterStudent: (String, Int, PackageType, LocalDate, Int, Boolean, String) -> Unit,
    onDeleteStudent: (Int) -> Unit,
    onCopyAttendance: (String) -> Unit,
    onMarkStudentPresent: (Int, LocalDate, Int) -> Unit,
    onRemoveStudentAttendance: (Int, LocalDate) -> Unit,
    onClearMarkStudentPresentState: () -> Unit,
    onClearRemoveStudentAttendanceState: () -> Unit,
    onLoadNextClassHistoryPage: () -> Unit,
    onRetryClassHistory: () -> Unit,
    onUpdateStudentNote: (Int, String) -> Unit,
    onClearUpdateStudentNoteState: () -> Unit,
    onRenewStudentPackage: (Int, Int, PackageType, LocalDate, Int, Boolean, String) -> Unit,
    onClearRenewStudentPackageState: () -> Unit,
) {
    var showRegistrationDialog by remember {
        mutableStateOf(false)
    }

    var deleteStudentDialog by remember {
        mutableStateOf<Int?>(null)
    }

    var attendanceDatePickerStudentId by remember {
        mutableStateOf<Int?>(null)
    }

    var attendanceRemovalRequest by remember {
        mutableStateOf<AttendanceRemovalRequest?>(null)
    }

    var renewalStudent by remember { mutableStateOf<RegisteredStudent?>(null) }

    var noteStudentId by remember { mutableStateOf<Int?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    when (state) {
        is AttendanceUiState.Content -> {
            val markState = state.markStudentPresentState
            val removeState = state.removeStudentAttendanceState
            val noteState = state.updateStudentNoteState
            val renewState = state.renewStudentPackageState

            LaunchedEffect(renewState) {
                if (renewState is RenewStudentPackageUiState.Success &&
                    renewState.studentId == renewalStudent?.student?.id
                ) {
                    renewalStudent = null
                    onClearRenewStudentPackageState()
                }
            }

            LaunchedEffect(noteState) {
                if (noteState is UpdateStudentNoteUiState.Success && noteState.studentId == noteStudentId) {
                    noteStudentId = null
                    onClearUpdateStudentNoteState()
                }
            }

            LaunchedEffect(markState) {
                if (markState == MarkStudentPresentUiState.Success) {
                    attendanceDatePickerStudentId = null
                    onClearMarkStudentPresentState()
                }
            }

            LaunchedEffect(removeState) {
                if (removeState == RemoveStudentAttendanceUiState.Success) {
                    attendanceRemovalRequest = null
                    onClearRemoveStudentAttendanceState()
                }
            }

            Scaffold(
                snackbarHost = {
                    SnackbarHost(
                        hostState = snackbarHostState,
                    )
                },
            ) { contentPadding ->
                AttendanceContent(
                    state = state,
                    onAddStudentClick = {
                        showRegistrationDialog = true
                    },
                    onDeleteStudentClick = { studentId ->
                        deleteStudentDialog = studentId
                    },
                    onCopyAttendanceClick = { groupId ->
                        val groupAttendance = state.attendance.copy(
                            entries = state.attendance.entries.filter { entry ->
                                entry.student.groupId == groupId
                            },
                        )

                        onCopyAttendance(
                            formatAttendanceForSharing(
                                attendance = groupAttendance,
                                currentDate = state.currentDate,
                            ),
                        )

                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                message = "Copied to clipboard",
                            )
                        }
                    },
                    onCheckStudentClick = { row ->
                        if (row.isCheckEnabled) {
                            val student = row.registeredStudent.student

                            if (row.requiresRenewal) {
                                onClearRenewStudentPackageState()
                                renewalStudent = row.registeredStudent
                            } else {
                                when (row.checkState) {
                                    AttendanceCheckState.Pending -> {
                                        onClearMarkStudentPresentState()
                                        attendanceDatePickerStudentId = student.id
                                    }

                                    AttendanceCheckState.Checked -> {
                                        onClearRemoveStudentAttendanceState()

                                        attendanceRemovalRequest =
                                            AttendanceRemovalRequest(
                                                studentId = student.id,
                                                studentName = student.fullName,
                                                attendanceDate = state.latestClassDate,
                                            )
                                    }
                                }
                            }
                        }
                    },
                    onRemoveStudentAttendanceClick = { row, date ->
                        val student = row.registeredStudent.student

                        onClearRemoveStudentAttendanceState()

                        attendanceRemovalRequest =
                            AttendanceRemovalRequest(
                                studentId = student.id,
                                studentName = student.fullName,
                                attendanceDate = date,
                            )
                    },
                    onLoadNextClassHistoryPage = onLoadNextClassHistoryPage,
                    onRetryClassHistory = onRetryClassHistory,
                    onEditNoteClick = { studentId ->
                        onClearUpdateStudentNoteState()
                        noteStudentId = studentId
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                )
            }

            if (showRegistrationDialog) {
                StudentRegistrationDialog(
                    studentGroups = state.studentGroups,
                    packageTypes = state.packageTypes,
                    initialDate = state.currentDate,
                    onRegister = {
                            fullName,
                            groupId,
                            packageType,
                            registeredOn,
                            visitCount,
                            recordFirstAttendance,
                            note,
                        ->
                        onRegisterStudent(
                            fullName,
                            groupId,
                            packageType,
                            registeredOn,
                            visitCount,
                            recordFirstAttendance,
                            note,
                        )
                        showRegistrationDialog = false
                    },
                    onDismiss = {
                        showRegistrationDialog = false
                    },
                )
            }

            renewalStudent?.let { registeredStudent ->
                val studentId = registeredStudent.student.id
                StudentPackageRenewalDialog(
                    registeredStudent = registeredStudent,
                    studentGroups = state.studentGroups,
                    packageTypes = state.packageTypes,
                    currentDate = state.currentDate,
                    isSaving = renewState == RenewStudentPackageUiState.Saving(studentId),
                    errorMessage = (renewState as? RenewStudentPackageUiState.Error)
                        ?.takeIf { it.studentId == studentId }?.message,
                    onRenew = { packageType, date, visits, recordAttendance, note ->
                        onRenewStudentPackage(
                            studentId,
                            registeredStudent.studentPackage.id,
                            packageType,
                            date,
                            visits,
                            recordAttendance,
                            note,
                        )
                    },
                    onDismiss = {
                        renewalStudent = null
                        onClearRenewStudentPackageState()
                    },
                )
            }

            noteStudentId?.let { studentId ->
                state.attendance.entries.firstOrNull { it.student.id == studentId }?.student?.let { student ->
                    StudentNoteDialog(
                        student = student,
                        isSaving = noteState == UpdateStudentNoteUiState.Saving(studentId),
                        errorMessage = (noteState as? UpdateStudentNoteUiState.Error)
                            ?.takeIf { it.studentId == studentId }
                            ?.message,
                        onSave = { note -> onUpdateStudentNote(studentId, note) },
                        onDismiss = {
                            noteStudentId = null
                            onClearUpdateStudentNoteState()
                        },
                    )
                }
            }

            attendanceDatePickerStudentId?.let { studentId ->
                val studentPackage =
                    state.attendance.entries
                        .first { it.student.id == studentId }
                        .studentPackage

                AttendanceDatePickerDialog(
                    initialDate = state.latestClassDate,
                    currentDate = state.currentDate,
                    maxVisitsPerAttendance =
                    studentPackage.packageType.maxVisitsPerAttendance,
                    remainingClasses = studentPackage.remainingClasses,
                    purchasedOn = studentPackage.purchasedOn,
                    startedOn = studentPackage.startedOn,
                    expiresOn = studentPackage.expiresOn,
                    recordedAttendanceDates = studentPackage.attendanceDates,
                    isSaving = markState == MarkStudentPresentUiState.Saving,
                    errorMessage =
                    (markState as? MarkStudentPresentUiState.Error)?.message,
                    onConfirm = { selectedDate, visitCount ->
                        onMarkStudentPresent(
                            studentId,
                            selectedDate,
                            visitCount,
                        )
                    },
                    onDismiss = {
                        attendanceDatePickerStudentId = null
                        onClearMarkStudentPresentState()
                    },
                )
            }

            attendanceRemovalRequest?.let { request ->
                AttendanceRemovalDialog(
                    studentName = request.studentName,
                    attendanceDate = request.attendanceDate,
                    isSaving = removeState == RemoveStudentAttendanceUiState.Saving,
                    errorMessage =
                    (removeState as? RemoveStudentAttendanceUiState.Error)?.message,
                    onConfirm = {
                        onRemoveStudentAttendance(
                            request.studentId,
                            request.attendanceDate,
                        )
                    },
                    onDismiss = {
                        attendanceRemovalRequest = null
                        onClearRemoveStudentAttendanceState()
                    },
                )
            }

            deleteStudentDialog?.let { studentId ->
                val student =
                    state.attendance.entries
                        .first { it.student.id == studentId }
                        .student

                AlertDialog(
                    onDismissRequest = {
                        deleteStudentDialog = null
                    },
                    title = {
                        Text(
                            text = "Delete student?",
                        )
                    },
                    text = {
                        Text(
                            text = "Delete \"${student.fullName}\"?",
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                onDeleteStudent(studentId)
                                deleteStudentDialog = null
                            },
                        ) {
                            Text(
                                text = "Delete",
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                deleteStudentDialog = null
                            },
                        ) {
                            Text(
                                text = "Cancel",
                            )
                        }
                    },
                )
            }
        }

        AttendanceUiState.Loading -> Unit

        is AttendanceUiState.Error -> {
            Box(
                modifier =
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun AttendanceContent(
    state: AttendanceUiState.Content,
    onAddStudentClick: () -> Unit,
    onDeleteStudentClick: (Int) -> Unit,
    onCopyAttendanceClick: (Int) -> Unit,
    onCheckStudentClick: (StudentAttendanceUiState) -> Unit,
    onRemoveStudentAttendanceClick: (StudentAttendanceUiState, LocalDate) -> Unit,
    onLoadNextClassHistoryPage: () -> Unit,
    onRetryClassHistory: () -> Unit,
    onEditNoteClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowsByGroup = state.rows.groupBy { row ->
        row.registeredStudent.student.groupId
    }

    LazyColumn(
        modifier = modifier.testTag("attendance-list"),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            bottom = 12.dp,
        ),
    ) {
        if (state.rows.isEmpty()) {
            item(key = "students-empty", contentType = "students-empty") {
                Text("No students yet")
            }
        }

        state.studentGroups.forEach { group ->
            val groupRows = rowsByGroup[group.id].orEmpty()

            if (groupRows.isNotEmpty()) {
                item(
                    key = "student-group-${group.id}",
                    contentType = "student-group-header",
                ) {
                    Column(
                        modifier = Modifier.testTag("student-group-${group.id}"),
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                top = 8.dp,
                                bottom = 2.dp,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = group.name,
                                style = MaterialTheme.typography.titleSmall,
                            )

                            Text(
                                text = "⧉",
                                modifier = Modifier
                                    .clickable(
                                        role = Role.Button,
                                        onClick = {
                                            onCopyAttendanceClick(group.id)
                                        },
                                    )
                                    .semantics {
                                        contentDescription =
                                            "Copy attendance for ${group.name}"
                                    }
                                    .padding(
                                        horizontal = 8.dp,
                                        vertical = 2.dp,
                                    ),
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 16.sp,
                            )
                        }

                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }

                itemsIndexed(
                    items = groupRows,
                    key = { _, row ->
                        "student-${row.registeredStudent.student.id}"
                    },
                    contentType = { _, _ -> "student" },
                ) { index, row ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("student-row-${row.registeredStudent.student.id}"),
                    ) {
                        StudentAttendanceRow(
                            row = row,
                            onCheckClick = {
                                onCheckStudentClick(row)
                            },
                            onDeleteClick = {
                                onDeleteStudentClick(row.registeredStudent.student.id)
                            },
                            onRemoveAttendanceClick = { date ->
                                onRemoveStudentAttendanceClick(row, date)
                            },
                        )

                        val student = row.registeredStudent.student

                        Text(
                            text = student.note.ifBlank { "Add note" },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (student.note.isBlank()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("student-note-${student.id}")
                                .clickable(role = Role.Button) { onEditNoteClick(student.id) }
                                .semantics { contentDescription = "Edit note for ${student.fullName}" }
                                .padding(top = 2.dp, bottom = 8.dp),
                        )

                        if (index < groupRows.lastIndex) {
                            HorizontalDivider(thickness = 0.5.dp)
                        }
                    }
                }
            }
        }

        item(key = "add-student", contentType = "add-student") {
            Button(onClick = onAddStudentClick) {
                Text("Add Student")
            }
        }

        classHistoryItems(
            state = state.classHistoryState,
            studentGroups = state.studentGroups,
            onLoadNextPage = onLoadNextClassHistoryPage,
            onRetry = onRetryClassHistory,
        )
    }
}

@Composable
private fun StudentAttendanceRow(
    row: StudentAttendanceUiState,
    onCheckClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onRemoveAttendanceClick: (LocalDate) -> Unit,
) {
    val registeredStudent = row.registeredStudent
    val studentPackage =
        registeredStudent.studentPackage

    var showPackageHistory by remember {
        mutableStateOf(false)
    }

    val attendanceInteractionSource = remember {
        MutableInteractionSource()
    }

    val isAttendanceHovered by
        attendanceInteractionSource.collectIsHoveredAsState()

    Row(
        modifier = Modifier.padding(
            vertical = 6.dp,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = registeredStudent.student.fullName,
            modifier = Modifier.width(AttendanceDimensions.NameColumnWidth),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Box(
            modifier = Modifier.width(AttendanceDimensions.ClassesColumnWidth),
        ) {
            Text(
                text =
                "${studentPackage.usedClasses} / " +
                    "${studentPackage.packageType.totalClasses}",
                modifier =
                Modifier
                    .hoverable(
                        interactionSource = attendanceInteractionSource,
                    )
                    .clickable {
                        showPackageHistory = true
                    }.semantics {
                        contentDescription =
                            "View package history for ${registeredStudent.student.fullName}"
                    },
                color = MaterialTheme.colorScheme.primary,
                textDecoration =
                if (isAttendanceHovered) {
                    TextDecoration.Underline
                } else {
                    TextDecoration.None
                },
            )

            DropdownMenu(
                expanded = showPackageHistory,
                onDismissRequest = {
                    showPackageHistory = false
                },
                modifier = Modifier.testTag("package-history-menu"),
            ) {
                Text(
                    text = "Package history",
                    modifier =
                    Modifier.padding(
                        horizontal = 16.dp,
                        vertical = 8.dp,
                    ),
                    fontWeight = FontWeight.SemiBold,
                )

                studentPackage.attendanceDates
                    .sorted()
                    .forEachIndexed { index, date ->
                        Row(
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = 16.dp,
                                    vertical = 4.dp,
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${index + 1}. ${date.toDisplayDate()}",
                                modifier = Modifier.weight(1f),
                            )

                            if (studentPackage.packageType.maxVisitsPerAttendance > 1) {
                                val visitCount =
                                    studentPackage.attendanceHistory.visitsByDate.getValue(date)

                                Text(
                                    text = visitCount.toString(),
                                    modifier = Modifier
                                        .padding(
                                            start = 12.dp,
                                            end = 8.dp,
                                        )
                                        .width(24.dp)
                                        .semantics {
                                            contentDescription =
                                                "Visits on ${date.toDisplayDate()} " +
                                                "for ${registeredStudent.student.fullName}"
                                            stateDescription = visitCount.toString()
                                        },
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            Box(
                                modifier = Modifier.width(32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "×",
                                    modifier =
                                    Modifier
                                        .clickable(
                                            role = Role.Button,
                                            onClick = {
                                                onRemoveAttendanceClick(date)
                                            },
                                        )
                                        .semantics {
                                            contentDescription =
                                                "Remove attendance on ${date.toDisplayDate()} " +
                                                "for ${registeredStudent.student.fullName}"
                                        }
                                        .padding(
                                            horizontal = 6.dp,
                                            vertical = 2.dp,
                                        ),
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 20.sp,
                                    lineHeight = 20.sp,
                                )
                            }
                        }
                    }
            }
        }

        Text(
            text = studentPackage.startedOn?.toDisplayDate().orEmpty(),
            modifier = Modifier.width(AttendanceDimensions.DateColumnWidth),
        )

        Text(
            text = studentPackage.expiresOn?.toDisplayDate().orEmpty(),
            modifier = Modifier.width(AttendanceDimensions.DateColumnWidth),
        )

        Text(
            text = studentPackage.packageType.name,
            modifier = Modifier.width(AttendanceDimensions.PackageColumnWidth),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = if (row.requiresRenewal) "♻️" else "✓",
            modifier =
            Modifier
                .clickable(
                    enabled = row.isCheckEnabled,
                    role = Role.Button,
                    onClick = onCheckClick,
                )
                .semantics {
                    contentDescription =
                        if (row.requiresRenewal) {
                            "Renew package for ${registeredStudent.student.fullName}"
                        } else {
                            "Mark ${registeredStudent.student.fullName} present"
                        }
                    stateDescription = if (row.requiresRenewal) "Renewal required" else row.checkState.name
                }
                .padding(
                    horizontal = 6.dp,
                    vertical = 2.dp,
                ),
            color =
            if (row.requiresRenewal) {
                MaterialTheme.colorScheme.primary
            } else {
                when (row.checkState) {
                    AttendanceCheckState.Pending ->
                        MaterialTheme.colorScheme.onSurfaceVariant

                    AttendanceCheckState.Checked ->
                        Color(0xFF2E7D32)
                }
            },
            fontSize = 20.sp,
            lineHeight = 20.sp,
        )

        Text(
            text = "×",
            modifier =
            Modifier
                .clickable(
                    onClick = onDeleteClick,
                )
                .semantics {
                    contentDescription =
                        "Delete ${registeredStudent.student.fullName}"
                }
                .padding(
                    horizontal = 6.dp,
                    vertical = 2.dp,
                ),
            color = MaterialTheme.colorScheme.error,
            fontSize = 20.sp,
            lineHeight = 20.sp,
        )
    }
}

internal fun LocalDate.toDisplayDate(): String {
    val month =
        month.name
            .take(3)
            .lowercase()
            .replaceFirstChar { it.uppercase() }

    return "$month $day"
}

private data class AttendanceRemovalRequest(val studentId: Int, val studentName: String, val attendanceDate: LocalDate)
