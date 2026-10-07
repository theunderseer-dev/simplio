package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AttendanceDatePickerDialog(onDismiss: () -> Unit) {
    val datePickerState = rememberDatePickerState()

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
            ) {
                Text(
                    text = "Cancel",
                )
            }
        },
    ) {
        DatePicker(
            state = datePickerState,
            title = {
                Text(
                    text = "Select attendance date",
                    modifier =
                    Modifier.padding(
                        horizontal = 24.dp,
                        vertical = 16.dp,
                    ),
                )
            },
        )
    }
}
