package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate

@Composable
internal fun AttendanceRemovalDialog(
    studentName: String,
    attendanceDate: LocalDate,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isSaving: Boolean = false,
    errorMessage: String? = null,
) {
    AlertDialog(
        onDismissRequest = {
            if (!isSaving) {
                onDismiss()
            }
        },
        title = {
            Text("Remove attendance?")
        },
        text = {
            Column {
                Text(
                    "Remove attendance for $studentName on ${attendanceDate.toDisplayDate()}?",
                )

                errorMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSaving,
                onClick = onConfirm,
            ) {
                Text(
                    text = if (isSaving) "Removing..." else "Remove",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isSaving,
                onClick = onDismiss,
            ) {
                Text("Cancel")
            }
        },
    )
}
