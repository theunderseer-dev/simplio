package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.key
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.schedule.AttendanceCheckState
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

@Composable
fun AttendanceScreen(
    state: AttendanceUiState,
    onRegisterStudent: (String, Int, PackageType, LocalDate, Int) -> Unit,
    onDeleteStudent: (Int) -> Unit,
    onCopyAttendance: (String) -> Unit,
    onMarkStudentPresent: (Int, LocalDate, Int) -> Unit,
    onRemoveStudentAttendance: (Int, LocalDate) -> Unit,
    onClearMarkStudentPresentState: () -> Unit,
    onClearRemoveStudentAttendanceState: () -> Unit,
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

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    when (state) {
        is AttendanceUiState.Content -> {
            val markState = state.markStudentPresentState
            val removeState = state.removeStudentAttendanceState

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
                Column(
                    modifier = Modifier.padding(contentPadding),
                ) {
                    AttendanceContent(
                        state = state,
                        onAddStudentClick = {
                            showRegistrationDialog = true
                        },
                        onDeleteStudentClick = { studentId ->
                            deleteStudentDialog = studentId
                        },
                        onCopyAttendanceClick = {
                            onCopyAttendance(
                                formatAttendanceForSharing(
                                    attendance = state.attendance,
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
                    )
                }
            }

            if (showRegistrationDialog) {
                StudentRegistrationDialog(
                    studentGroups = state.studentGroups,
                    packageTypes = state.packageTypes,
                    initialDate = state.currentDate,
                    onRegister = { fullName, groupId, packageType, registeredOn, visitCount ->
                        onRegisterStudent(
                            fullName,
                            groupId,
                            packageType,
                            registeredOn,
                            visitCount,
                        )
                        showRegistrationDialog = false
                    },
                    onDismiss = {
                        showRegistrationDialog = false
                    },
                )
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
    onCopyAttendanceClick: () -> Unit,
    onCheckStudentClick: (StudentAttendanceUiState) -> Unit,
    onRemoveStudentAttendanceClick: (StudentAttendanceUiState, LocalDate) -> Unit,
) {
    Column(
        modifier = Modifier.padding(
            horizontal = 12.dp,
        ),
    ) {
        if (state.rows.isEmpty()) {
            Text(
                text = "No students yet",
            )
        } else {
            val rowsByGroup =
                state.rows.groupBy { row ->
                    row.registeredStudent.student.groupId
                }

            state.studentGroups.forEach { group ->
                val groupRows = rowsByGroup[group.id].orEmpty()

                if (groupRows.isNotEmpty()) {
                    key(group.id) {
                        Column(
                            modifier = Modifier.testTag("student-group-${group.id}"),
                        ) {
                            Text(
                                text = group.name,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(
                                    top = 12.dp,
                                    bottom = 6.dp,
                                ),
                            )

                            groupRows.forEachIndexed { index, row ->
                                key(row.registeredStudent.student.id) {
                                    StudentAttendanceRow(
                                        row = row,
                                        onCheckClick = {
                                            onCheckStudentClick(row)
                                        },
                                        onDeleteClick = {
                                            onDeleteStudentClick(
                                                row.registeredStudent.student.id,
                                            )
                                        },
                                        onRemoveAttendanceClick = { date ->
                                            onRemoveStudentAttendanceClick(row, date)
                                        },
                                    )

                                    if (index < groupRows.lastIndex) {
                                        HorizontalDivider(
                                            thickness = 0.5.dp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onAddStudentClick,
            ) {
                Text("Add Student")
            }

            Text(
                text = "⧉",
                modifier =
                Modifier
                    .clickable(
                        onClick = onCopyAttendanceClick,
                    )
                    .semantics {
                        contentDescription = "Copy attendance"
                    }
                    .padding(8.dp),
            )
        }
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

                            if (date != studentPackage.startedOn) {
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
            text = studentPackage.startedOn.toDisplayDate(),
            modifier = Modifier.width(AttendanceDimensions.DateColumnWidth),
        )

        Text(
            text = studentPackage.expiresOn.toDisplayDate(),
            modifier = Modifier.width(AttendanceDimensions.DateColumnWidth),
        )

        Text(
            text = studentPackage.packageType.name,
            modifier = Modifier.width(AttendanceDimensions.PackageColumnWidth),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = "✓",
            modifier =
            Modifier
                .clickable(
                    enabled = row.isCheckEnabled,
                    onClick = onCheckClick,
                )
                .semantics {
                    contentDescription =
                        "Mark ${registeredStudent.student.fullName} present"
                    stateDescription = row.checkState.name
                }
                .padding(
                    horizontal = 6.dp,
                    vertical = 2.dp,
                ),
            color =
            when (row.checkState) {
                AttendanceCheckState.Pending ->
                    MaterialTheme.colorScheme.onSurfaceVariant

                AttendanceCheckState.Checked ->
                    Color(0xFF2E7D32)
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
