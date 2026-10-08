package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import kotlinx.datetime.LocalDate

@Composable
internal fun AttendanceRemovalDialog(
    studentName: String,
    attendanceDate: LocalDate,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Remove attendance?")
        },
        text = {
            Text(
                text = "Remove attendance for $studentName on ${attendanceDate.toDisplayDate()}?",
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(text = "Remove")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        },
    )
}
