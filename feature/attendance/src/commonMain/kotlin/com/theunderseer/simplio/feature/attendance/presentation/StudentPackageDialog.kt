package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.schedule.isAttendanceDateSelectable
import com.theunderseer.simplio.feature.attendance.domain.schedule.latestScheduledClassDate
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StudentPackageDialog(
    title: String,
    confirmLabel: String,
    dateLabel: String,
    datePickerTitle: String,
    packageTypes: List<PackageType>,
    initialDate: LocalDate,
    initialPackageTypeId: Int? = null,
    earliestDate: LocalDate? = null,
    initialNote: String = "",
    isSaving: Boolean = false,
    errorMessage: String? = null,
    canSubmit: Boolean = true,
    studentFields: @Composable () -> Unit,
    onConfirm: (PackageType, LocalDate, Int, Boolean, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var note by remember { mutableStateOf(initialNote) }

    var selectedPackage by remember(packageTypes, initialPackageTypeId) {
        mutableStateOf(
            if (initialPackageTypeId != null) {
                packageTypes.firstOrNull { it.id == initialPackageTypeId } ?: packageTypes.firstOrNull()
            } else {
                packageTypes.firstOrNull { it.totalClasses == 4 && it.maxVisitsPerAttendance == 1 }
            },
        )
    }

    var recordFirstAttendance by remember {
        mutableStateOf(true)
    }

    val isPairedPackage =
        selectedPackage?.maxVisitsPerAttendance == 2

    var selectedVisitCount by remember(selectedPackage) {
        mutableIntStateOf(
            if (isPairedPackage && checkNotNull(selectedPackage).totalClasses >= 2) 2 else 1,
        )
    }

    var isPackageMenuExpanded by remember {
        mutableStateOf(false)
    }

    var selectedDate by remember(initialDate, earliestDate) {
        mutableStateOf(initialPackagePurchaseDate(initialDate, earliestDate))
    }

    var showDatePicker by remember {
        mutableStateOf(false)
    }

    val packageExpirationDate =
        if (recordFirstAttendance) {
            selectedPackage?.let { packageType ->
                selectedDate?.plus(
                    DatePeriod(days = packageType.validityWeeks * 7),
                )
            }
        } else {
            null
        }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = {
            Text(title)
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                studentFields()

                Box {
                    OutlinedTextField(
                        enabled = !isSaving,
                        value = selectedDate?.toDisplayDate().orEmpty(),
                        supportingText = if (selectedDate == null) {
                            { Text("No scheduled dates available yet") }
                        } else {
                            null
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = {
                            Text(dateLabel)
                        },
                    )

                    Box(
                        modifier =
                        Modifier
                            .matchParentSize()
                            .clickable(enabled = !isSaving) {
                                showDatePicker = true
                            }
                            .semantics {
                                contentDescription = dateLabel
                            },
                    )
                }

                ExposedDropdownMenuBox(
                    expanded = isPackageMenuExpanded,
                    onExpandedChange = {
                        if (!isSaving) isPackageMenuExpanded = it
                    },
                ) {
                    OutlinedTextField(
                        enabled = !isSaving,
                        value = selectedPackage?.name.orEmpty(),
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = {
                            Text("Package")
                        },
                        supportingText =
                        if (packageExpirationDate != null) {
                            {
                                Text(
                                    text = "Valid until ${packageExpirationDate.toDisplayDate()}, " +
                                        "${packageExpirationDate.year}",
                                )
                            }
                        } else {
                            null
                        },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(
                                expanded = isPackageMenuExpanded,
                            )
                        },
                        modifier =
                        Modifier.testTag("package-purchase-package").menuAnchor(
                            type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                            enabled = packageTypes.isNotEmpty() && !isSaving,
                        ),
                    )

                    ExposedDropdownMenu(
                        expanded = isPackageMenuExpanded,
                        onDismissRequest = {
                            isPackageMenuExpanded = false
                        },
                    ) {
                        packageTypes.forEach { packageType ->
                            DropdownMenuItem(
                                enabled = !isSaving,
                                text = {
                                    Text(packageType.name)
                                },
                                onClick = {
                                    selectedPackage = packageType
                                    isPackageMenuExpanded = false
                                },
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(
                            value = recordFirstAttendance,
                            enabled = !isSaving,
                            role = Role.Checkbox,
                            onValueChange = {
                                recordFirstAttendance = it
                            },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = recordFirstAttendance,
                        onCheckedChange = null,
                    )

                    Text(
                        text = "Record first attendance",
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }

                if (isPairedPackage && recordFirstAttendance) {
                    Column(
                        modifier = Modifier.padding(top = 16.dp),
                    ) {
                        Text("People attending")

                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .heightIn(min = 48.dp),
                        ) {
                            SegmentedButton(
                                enabled = !isSaving,
                                selected = selectedVisitCount == 1,
                                onClick = {
                                    selectedVisitCount = 1
                                },
                                shape =
                                SegmentedButtonDefaults.itemShape(
                                    index = 0,
                                    count = 2,
                                ),
                            ) {
                                Text("1 person")
                            }

                            SegmentedButton(
                                enabled = !isSaving,
                                selected = selectedVisitCount == 2,
                                onClick = {
                                    selectedVisitCount = 2
                                },
                                shape =
                                SegmentedButtonDefaults.itemShape(
                                    index = 1,
                                    count = 2,
                                ),
                            ) {
                                Text("2 people")
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = note,
                    enabled = !isSaving,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .testTag("registration-note"),
                )

                errorMessage?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                enabled =
                !isSaving &&
                    canSubmit &&
                    selectedPackage != null &&
                    selectedDate?.let { isPackagePurchaseDateSelectable(it, initialDate, earliestDate) } == true &&
                    (!recordFirstAttendance || checkNotNull(selectedPackage).allowsVisitCount(selectedVisitCount)),
                onClick = {
                    onConfirm(
                        checkNotNull(selectedPackage),
                        checkNotNull(selectedDate),
                        selectedVisitCount,
                        recordFirstAttendance,
                        note.trim(),
                    )
                },
            ) {
                Text(if (isSaving) "Saving..." else confirmLabel)
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

    if (showDatePicker) {
        val selectableDates =
            remember(initialDate, earliestDate) {
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                        val date =
                            Instant
                                .fromEpochMilliseconds(utcTimeMillis)
                                .toLocalDateTime(TimeZone.UTC)
                                .date

                        return isPackagePurchaseDateSelectable(date, initialDate, earliestDate)
                    }
                }
            }

        val datePickerState =
            rememberDatePickerState(
                initialSelectedDateMillis =
                selectedDate
                    ?.atStartOfDayIn(TimeZone.UTC)
                    ?.toEpochMilliseconds(),
                initialDisplayedMonthMillis = initialDate.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
                selectableDates = selectableDates,
            )

        DatePickerDialog(
            onDismissRequest = {
                showDatePicker = false
            },
            confirmButton = {
                TextButton(
                    enabled = datePickerState.selectedDateMillis?.let { selectableDates.isSelectableDate(it) } == true,
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
                Text(datePickerTitle)

                DatePicker(
                    state = datePickerState,
                )
            }
        }
    }
}

internal fun isPackagePurchaseDateSelectable(date: LocalDate, today: LocalDate, earliestDate: LocalDate?): Boolean =
    isAttendanceDateSelectable(date, today) && (earliestDate == null || date >= earliestDate)

internal fun initialPackagePurchaseDate(today: LocalDate, earliestDate: LocalDate?): LocalDate? =
    latestScheduledClassDate(today).takeIf { isPackagePurchaseDateSelectable(it, today, earliestDate) }
