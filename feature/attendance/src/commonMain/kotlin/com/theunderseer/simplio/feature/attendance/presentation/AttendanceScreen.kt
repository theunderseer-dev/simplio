package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent

@Composable
fun AttendanceScreen(state: AttendanceUiState, onRegisterStudent: (String, PackageType) -> Unit) {
    var showRegistrationDialog by remember {
        mutableStateOf(false)
    }

    when (state) {
        is AttendanceUiState.Content -> {
            AttendanceContent(
                state = state,
                onAddStudentClick = {
                    showRegistrationDialog = true
                },
            )

            if (showRegistrationDialog) {
                StudentRegistrationDialog(
                    packageTypes = state.packageTypes,
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
        }

        AttendanceUiState.Loading -> Unit

        is AttendanceUiState.Error -> Unit
    }
}

@Composable
private fun AttendanceContent(state: AttendanceUiState.Content, onAddStudentClick: () -> Unit) {
    Column(
        modifier =
        Modifier.padding(
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
                )

                if (index < state.attendance.entries.lastIndex) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                    )
                }
            }
        }

        Button(
            onClick = onAddStudentClick,
        ) {
            Text(
                text = "Add Student",
            )
        }
    }
}

@Composable
private fun StudentAttendanceRow(registeredStudent: RegisteredStudent) {
    val studentPackage =
        registeredStudent.studentPackage

    Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        modifier =
        Modifier.padding(
            vertical = 6.dp,
        ),
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
            text = studentPackage.startedOn.toString(),
            modifier = Modifier.width(AttendanceDimensions.DateColumnWidth),
        )

        Text(
            text = studentPackage.expiresOn.toString(),
            modifier = Modifier.width(AttendanceDimensions.DateColumnWidth),
        )

        Text(
            text = studentPackage.packageType.name,
            modifier = Modifier.width(AttendanceDimensions.PackageColumnWidth),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
