package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentRegistrationDialog(
    packageTypes: List<PackageType>,
    initialDate: LocalDate,
    onRegister: (String, PackageType, LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    var fullName by remember {
        mutableStateOf("")
    }

    var selectedPackage by remember {
        mutableStateOf<PackageType?>(null)
    }

    var selectedDate by remember(initialDate) {
        mutableStateOf(initialDate)
    }

    var showDatePicker by remember {
        mutableStateOf(false)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Add Student")
        },
        text = {
            Column {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = {
                        Text("Full name")
                    },
                )

                Box {
                    OutlinedTextField(
                        value = selectedDate.toDisplayDate(),
                        onValueChange = {},
                        readOnly = true,
                        label = {
                            Text("Registration date")
                        },
                    )

                    Box(
                        modifier =
                        Modifier
                            .matchParentSize()
                            .clickable {
                                showDatePicker = true
                            }
                            .semantics {
                                contentDescription = "Registration date"
                            },
                    )
                }

                packageTypes.forEach { packageType ->
                    TextButton(
                        onClick = {
                            selectedPackage = packageType
                        },
                    ) {
                        Text(packageType.name)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled =
                fullName.isNotBlank() &&
                    selectedPackage != null,
                onClick = {
                    onRegister(
                        fullName.trim(),
                        checkNotNull(selectedPackage),
                        selectedDate,
                    )
                },
            ) {
                Text("Register")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
            ) {
                Text("Cancel")
            }
        },
    )

    if (showDatePicker) {
        val selectableDates =
            remember(initialDate) {
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                        val date =
                            Instant
                                .fromEpochMilliseconds(utcTimeMillis)
                                .toLocalDateTime(TimeZone.UTC)
                                .date

                        return isRegistrationDateSelectable(
                            date = date,
                            today = initialDate,
                        )
                    }
                }
            }

        val datePickerState =
            rememberDatePickerState(
                initialSelectedDateMillis =
                selectedDate
                    .atStartOfDayIn(TimeZone.UTC)
                    .toEpochMilliseconds(),
                selectableDates = selectableDates,
            )

        DatePickerDialog(
            onDismissRequest = {
                showDatePicker = false
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            selectedDate =
                                Instant
                                    .fromEpochMilliseconds(millis)
                                    .toLocalDateTime(TimeZone.UTC)
                                    .date
                        }

                        showDatePicker = false
                    },
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDatePicker = false
                    },
                ) {
                    Text("Cancel")
                }
            },
        ) {
            Column {
                Text("Select registration date")

                DatePicker(
                    state = datePickerState,
                )
            }
        }
    }
}

internal fun isRegistrationDateSelectable(date: LocalDate, today: LocalDate): Boolean = date <= today
