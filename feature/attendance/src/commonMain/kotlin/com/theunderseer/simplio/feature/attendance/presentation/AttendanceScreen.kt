package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType

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
    Column {
        if (state.attendance.entries.isEmpty()) {
            Text(
                text = "No students yet",
            )
        } else {
            state.attendance.entries.forEach { registeredStudent ->
                Text(
                    text = registeredStudent.student.fullName,
                )

                Text(
                    text =
                    "${registeredStudent.studentPackage.usedClasses} / " +
                        "${registeredStudent.studentPackage.packageType.totalClasses}",
                )

                Text(
                    text = registeredStudent.studentPackage.packageType.name,
                )
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
