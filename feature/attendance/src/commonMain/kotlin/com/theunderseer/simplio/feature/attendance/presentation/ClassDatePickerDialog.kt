package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ClassDatePickerDialog(
    title: String,
    state: DatePickerState,
    canConfirm: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isSaving: Boolean = false,
    availabilityKey: Any? = null,
    supportingContent: @Composable ColumnScope.() -> Unit = {},
) {
    DatePickerDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        confirmButton = {
            TextButton(
                enabled = canConfirm && !isSaving,
                onClick = { if (canConfirm && !isSaving) onConfirm() },
            ) {
                Text(if (isSaving) "Saving..." else "OK")
            }
        },
        dismissButton = {
            TextButton(enabled = !isSaving, onClick = onDismiss) {
                Text("Cancel")
            }
        },
    ) {
        Column {
            key(availabilityKey) {
                DatePicker(
                    state = state,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .testTag("class-date-picker"),
                    title = {
                        Text(
                            text = title,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        )
                    },
                )
            }
            supportingContent()
        }
    }
}
