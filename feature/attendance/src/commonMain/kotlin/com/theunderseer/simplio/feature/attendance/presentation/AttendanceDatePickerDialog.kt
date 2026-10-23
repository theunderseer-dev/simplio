package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.theunderseer.simplio.feature.attendance.domain.model.ClassSchedule
import com.theunderseer.simplio.feature.attendance.domain.schedule.isAttendanceDateSelectable
import com.theunderseer.simplio.feature.attendance.domain.schedule.isScheduledClassDate
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
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
    schedules: List<ClassSchedule>? = null,
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
    val schedulesState = rememberUpdatedState(schedules)

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
                        (
                            schedulesState.value?.let {
                                date <= currentDateState.value &&
                                    it.isScheduledClassDate(
                                        date,
                                    )
                            }
                                ?: isAttendanceDateSelectable(date, currentDateState.value)
                            )
                }
            }
        }

    val hasAvailableDates =
        remember(currentDate, purchasedOn, startedOn, expiresOn, recordedAttendanceDates, schedules) {
            val earliestDate = startedOn ?: purchasedOn
            val latestDate = minOf(currentDate, expiresOn ?: currentDate)

            generateSequence(latestDate) { date ->
                date - DatePeriod(days = 1)
            }
                .takeWhile { it >= earliestDate }
                .any { date ->
                    selectableDates.isSelectableDate(date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds())
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

    ClassDatePickerDialog(
        title = "Select attendance date",
        state = datePickerState,
        canConfirm = canConfirm,
        isSaving = isSaving,
        availabilityKey = listOf(currentDate, recordedAttendanceDates, purchasedOn, startedOn, expiresOn, schedules),
        onConfirm = {
            selectedDate?.let { date -> onConfirm(date, selectedVisitCount) }
        },
        onDismiss = onDismiss,
    ) {
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

        if (!hasAvailableDates) {
            Text(
                text = "No classes to check yet",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
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
