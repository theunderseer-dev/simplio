package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.theunderseer.simplio.feature.attendance.domain.schedule.isAttendanceDateSelectable
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AttendanceDatePickerDialog(initialDate: LocalDate, currentDate: LocalDate, onDismiss: () -> Unit) {
    val currentDateState = rememberUpdatedState(currentDate)

    val selectableDates =
        remember {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val date =
                        Instant
                            .fromEpochMilliseconds(utcTimeMillis)
                            .toLocalDateTime(TimeZone.UTC)
                            .date

                    return isAttendanceDateSelectable(
                        date = date,
                        today = currentDateState.value,
                    )
                }
            }
        }

    val datePickerState =
        rememberDatePickerState(
            initialSelectedDateMillis =
            initialDate
                .atStartOfDayIn(TimeZone.UTC)
                .toEpochMilliseconds(),
            selectableDates = selectableDates,
        )

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
        key(currentDate) {
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
}
