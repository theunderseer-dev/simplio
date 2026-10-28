package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.theunderseer.simplio.feature.attendance.domain.model.Student
import com.theunderseer.simplio.shared.presentation.components.SimplioTextButton

@Composable
internal fun StudentNoteDialog(
    student: Student,
    isSaving: Boolean,
    errorMessage: String?,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var note by remember(student.id) { mutableStateOf(student.note) }

    AlertDialog(
        onDismissRequest = {
            if (!isSaving) onDismiss()
        },
        title = { Text("Note for ${student.fullName}") },
        text = {
            Column {
                OutlinedTextField(
                    shape = MaterialTheme.shapes.small,
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth().testTag("student-note-input"),
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            SimplioTextButton(
                enabled = !isSaving,
                onClick = { onSave(note.trim()) },
            ) {
                Text(if (isSaving) "Saving..." else "Save")
            }
        },
        dismissButton = {
            SimplioTextButton(enabled = !isSaving, onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
