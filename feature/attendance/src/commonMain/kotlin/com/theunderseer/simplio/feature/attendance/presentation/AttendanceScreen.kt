package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent
import com.theunderseer.simplio.feature.attendance.domain.schedule.AttendanceCheckState
import com.theunderseer.simplio.feature.attendance.domain.schedule.attendanceCheckState
import com.theunderseer.simplio.feature.attendance.domain.schedule.latestScheduledClassDate
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

@Composable
fun AttendanceScreen(
    state: AttendanceUiState,
    currentDate: LocalDate,
    onRegisterStudent: (String, PackageType, LocalDate) -> Unit,
    onDeleteStudent: (Int) -> Unit,
    onCopyAttendance: (String) -> Unit,
    onCheckStudent: (Int) -> Unit,
) {
    var showRegistrationDialog by remember {
        mutableStateOf(false)
    }

    var deleteStudentDialog by remember {
        mutableStateOf<Int?>(null)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    when (state) {
        is AttendanceUiState.Content -> {
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
                                    currentDate = currentDate,
                                ),
                            )

                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    message = "Copied to clipboard",
                                )
                            }
                        },
                        onCheckStudentClick = onCheckStudent,
                        latestClassDate = latestScheduledClassDate(
                            today = currentDate,
                        ),
                    )
                }
            }

            if (showRegistrationDialog) {
                StudentRegistrationDialog(
                    packageTypes = state.packageTypes,
                    initialDate = currentDate,
                    onRegister = { fullName, packageType, registeredOn ->
                        onRegisterStudent(
                            fullName,
                            packageType,
                            registeredOn,
                        )
                        showRegistrationDialog = false
                    },
                    onDismiss = {
                        showRegistrationDialog = false
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

        is AttendanceUiState.Error -> Unit
    }
}

@Composable
private fun AttendanceContent(
    state: AttendanceUiState.Content,
    onAddStudentClick: () -> Unit,
    onDeleteStudentClick: (Int) -> Unit,
    onCopyAttendanceClick: () -> Unit,
    onCheckStudentClick: (Int) -> Unit,
    latestClassDate: LocalDate,
) {
    Column(
        modifier = Modifier.padding(
            horizontal = 12.dp,
        ),
    ) {
        if (state.attendance.entries.isEmpty()) {
            Text(
                text = "No students yet",
            )
        } else {
            state.attendance.entries.forEachIndexed { index, registeredStudent ->
                StudentAttendanceRow(
                    registeredStudent = registeredStudent,
                    checkState = attendanceCheckState(
                        attendanceDates = registeredStudent.studentPackage.attendanceDates,
                        latestScheduledClassDate = latestClassDate,
                    ),
                    onCheckClick = {
                        onCheckStudentClick(
                            registeredStudent.student.id,
                        )
                    },
                    onDeleteClick = {
                        onDeleteStudentClick(
                            registeredStudent.student.id,
                        )
                    },
                )

                if (index < state.attendance.entries.lastIndex) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                    )
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
    registeredStudent: RegisteredStudent,
    checkState: AttendanceCheckState,
    onCheckClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val studentPackage =
        registeredStudent.studentPackage

    var showAttendanceDates by remember {
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
                            showAttendanceDates = true
                        }.semantics {
                            contentDescription =
                                "View attendance dates for ${registeredStudent.student.fullName}"
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
                expanded = showAttendanceDates,
                onDismissRequest = {
                    showAttendanceDates = false
                },
                modifier = Modifier.testTag("attendance-dates-menu"),
            ) {
                Text(
                    text = "Attendance dates",
                    modifier = Modifier.padding(
                        horizontal = 16.dp,
                        vertical = 8.dp,
                    ),
                    fontWeight = FontWeight.SemiBold,
                )

                studentPackage.attendanceDates
                    .sorted()
                    .forEachIndexed { index, date ->
                        Text(
                            text = "${index + 1}. ${date.toDisplayDate()}",
                            modifier = Modifier.padding(
                                horizontal = 16.dp,
                                vertical = 4.dp,
                            ),
                        )
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
                        onClick = onCheckClick,
                    )
                    .semantics {
                        contentDescription =
                            "Mark ${registeredStudent.student.fullName} present"
                        stateDescription = checkState.name
                    }
                    .padding(
                        horizontal = 6.dp,
                        vertical = 2.dp,
                    ),
            color =
                when (checkState) {
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
