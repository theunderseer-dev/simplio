package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
internal fun AttendanceDatePickerDialog(
    initialDate: LocalDate,
    currentDate: LocalDate,
    maxVisitsPerAttendance: Int,
    remainingClasses: Int,
    purchasedOn: LocalDate,
    startedOn: LocalDate?,
    expiresOn: LocalDate?,
    recordedAttendanceDates: Set<LocalDate>,
    isSaving: Boolean = false,
    errorMessage: String? = null,
    onConfirm: (LocalDate, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val isDuoPackage = maxVisitsPerAttendance == 2

    var selectedVisitCount by remember(
        maxVisitsPerAttendance,
        remainingClasses,
    ) {
        mutableIntStateOf(
            if (isDuoPackage && remainingClasses >= 2) 2 else 1,
        )
    }

    val canConfirmVisitCount =
        selectedVisitCount in 1..maxVisitsPerAttendance &&
            selectedVisitCount <= remainingClasses

    val purchasedOnState = rememberUpdatedState(purchasedOn)
    val startedOnState = rememberUpdatedState(startedOn)
    val expiresOnState = rememberUpdatedState(expiresOn)

    val currentDateState = rememberUpdatedState(currentDate)

    val recordedAttendanceDatesState =
        rememberUpdatedState(recordedAttendanceDates)

    val selectableDates =
        remember {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val date =
                        Instant
                            .fromEpochMilliseconds(utcTimeMillis)
                            .toLocalDateTime(TimeZone.UTC)
                            .date

                    val earliestDate =
                        startedOnState.value ?: purchasedOnState.value

                    val expirationDate = expiresOnState.value

                    return date >= earliestDate &&
                        (expirationDate == null || date <= expirationDate) &&
                        date !in recordedAttendanceDatesState.value &&
                        isAttendanceDateSelectable(
                            date = date,
                            today = currentDateState.value,
                        )
                }
            }
        }

    val initialDateMillis =
        initialDate
            .atStartOfDayIn(TimeZone.UTC)
            .toEpochMilliseconds()

    val datePickerState =
        rememberDatePickerState(
            initialSelectedDateMillis =
            initialDateMillis.takeIf { millis ->
                selectableDates.isSelectableDate(millis)
            },
            initialDisplayedMonthMillis = initialDateMillis,
            selectableDates = selectableDates,
        )

    val selectedDate =
        datePickerState.selectedDateMillis?.let { millis ->
            Instant
                .fromEpochMilliseconds(millis)
                .toLocalDateTime(TimeZone.UTC)
                .date
        }

    val canConfirmDate =
        datePickerState.selectedDateMillis?.let { millis ->
            selectableDates.isSelectableDate(millis)
        } ?: false

    val canConfirm =
        canConfirmDate && canConfirmVisitCount && !isSaving

    DatePickerDialog(
        onDismissRequest = {
            if (!isSaving) {
                onDismiss()
            }
        },
        confirmButton = {
            TextButton(
                enabled = canConfirm,
                onClick = {
                    if (canConfirm) {
                        selectedDate?.let { date ->
                            onConfirm(date, selectedVisitCount)
                        }
                    }
                },
            ) {
                Text(
                    text = if (isSaving) "Saving..." else "OK",
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
    ) {
        Column {
            key(
                currentDate,
                recordedAttendanceDates,
                purchasedOn,
                startedOn,
                expiresOn,
            ) {
                DatePicker(
                    state = datePickerState,
                    modifier =
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
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

            if (isDuoPackage) {
                Column(
                    modifier =
                    Modifier.padding(
                        horizontal = 24.dp,
                        vertical = 8.dp,
                    ),
                ) {
                    Text("People attending")

                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        SegmentedButton(
                            selected = selectedVisitCount == 1,
                            onClick = {
                                selectedVisitCount = 1
                            },
                            enabled = !isSaving,
                            shape =
                            SegmentedButtonDefaults.itemShape(
                                index = 0,
                                count = 2,
                            ),
                        ) {
                            Text("1 person")
                        }

                        SegmentedButton(
                            selected = selectedVisitCount == 2,
                            onClick = {
                                selectedVisitCount = 2
                            },
                            enabled = remainingClasses >= 2 && !isSaving,
                            shape =
                            SegmentedButtonDefaults.itemShape(
                                index = 1,
                                count = 2,
                            ),
                        ) {
                            Text("2 people")
                        }
                    }

                    if (remainingClasses == 1) {
                        Text(
                            text = "Only 1 visit remaining.",
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }

            errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier =
                    Modifier.padding(
                        horizontal = 24.dp,
                        vertical = 8.dp,
                    ),
                )
            }
        }
    }
}
