package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.theunderseer.simplio.feature.attendance.resources.Res
import com.theunderseer.simplio.feature.attendance.resources.package_cancel
import com.theunderseer.simplio.feature.attendance.resources.student_delete_button
import com.theunderseer.simplio.feature.attendance.resources.student_delete_confirmation
import com.theunderseer.simplio.feature.attendance.resources.student_delete_title
import com.theunderseer.simplio.feature.attendance.resources.student_deleting
import com.theunderseer.simplio.shared.presentation.components.SimplioButton
import com.theunderseer.simplio.shared.presentation.components.SimplioTextButton
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun StudentDeletionDialog(
    studentName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isSaving: Boolean = false,
    errorMessage: String? = null,
) {
    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(stringResource(Res.string.student_delete_title)) },
        text = {
            Column {
                Text(stringResource(Res.string.student_delete_confirmation, studentName))
                errorMessage?.let { message ->
                    Text(
                        text = message,
                        modifier = Modifier.padding(top = 12.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            SimplioButton(onClick = onConfirm, enabled = !isSaving) {
                Text(stringResource(if (isSaving) Res.string.student_deleting else Res.string.student_delete_button))
            }
        },
        dismissButton = {
            SimplioTextButton(onClick = onDismiss, enabled = !isSaving) {
                Text(stringResource(Res.string.package_cancel))
            }
        },
    )
}
