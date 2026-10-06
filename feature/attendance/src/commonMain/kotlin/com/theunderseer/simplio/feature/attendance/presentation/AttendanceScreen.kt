package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

@Composable
fun AttendanceScreen(
    state: AttendanceUiState,
    currentDate: LocalDate,
    onRegisterStudent: (String, PackageType) -> Unit,
    onDeleteStudent: (Int) -> Unit,
    onCopyAttendance: (String) -> Unit,
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
                        onCopyAttendance = {
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
                    )
                }
            }

            if (showRegistrationDialog) {
                StudentRegistrationDialog(
                    packageTypes = state.packageTypes,
                    initialDate = currentDate,
                    onRegister = { fullName, packageType ->
                        onRegisterStudent(
                            fullName,
                            packageType,
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
    onCopyAttendance: () -> Unit,
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
                        onClick = onCopyAttendance,
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
private fun StudentAttendanceRow(registeredStudent: RegisteredStudent, onDeleteClick: () -> Unit) {
    val studentPackage =
        registeredStudent.studentPackage

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

        Text(
            text =
            "${studentPackage.usedClasses} / " +
                "${studentPackage.packageType.totalClasses}",
            modifier = Modifier.width(AttendanceDimensions.ClassesColumnWidth),
        )

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
