package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

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
    val scrollState = rememberScrollState()

    // DatePickerDialog caps the whole dialog's height, leaving too little space for
    // the calendar when attendance controls or messages are displayed below it.
    BasicAlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        modifier = Modifier.widthIn(max = 360.dp).padding(vertical = 24.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = DatePickerDefaults.shape,
            color = DatePickerDefaults.colors().containerColor,
            tonalElevation = DatePickerDefaults.TonalElevation,
        ) {
            Column {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(scrollState)
                        .testTag("class-date-picker-content"),
                ) {
                    key(availabilityKey) {
                        DatePicker(
                            state = state,
                            modifier = Modifier.testTag("class-date-picker"),
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
                if (scrollState.canScrollForward) {
                    Text(
                        text = "Scroll for more",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(enabled = !isSaving, onClick = onDismiss) {
                        Text("Cancel")
                    }
                    TextButton(
                        enabled = canConfirm && !isSaving,
                        onClick = { if (canConfirm && !isSaving) onConfirm() },
                    ) {
                        Text(if (isSaving) "Saving..." else "OK")
                    }
                }
            }
        }
    }
}
