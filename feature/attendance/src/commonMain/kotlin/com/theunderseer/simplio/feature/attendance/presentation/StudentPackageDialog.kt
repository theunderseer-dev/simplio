package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.theunderseer.simplio.feature.attendance.domain.model.ClassSchedule
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.schedule.isAttendanceDateSelectable
import com.theunderseer.simplio.feature.attendance.domain.schedule.isRegistrationClassDate
import com.theunderseer.simplio.feature.attendance.domain.schedule.latestRegistrationClassDate
import com.theunderseer.simplio.feature.attendance.domain.schedule.latestScheduledClassDate
import com.theunderseer.simplio.feature.attendance.resources.Res
import com.theunderseer.simplio.feature.attendance.resources.package_no_expiration
import com.theunderseer.simplio.feature.attendance.resources.package_valid_until
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
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
    recordedAttendanceDates: Set<LocalDate> = emptySet(),
    initialNote: String = "",
    isSaving: Boolean = false,
    errorMessage: String? = null,
    canSubmit: Boolean = true,
    schedules: List<ClassSchedule>? = null,
    studentFields: @Composable () -> Unit,
    onConfirm: (PackageType, LocalDate, Int, Boolean, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var note by remember { mutableStateOf(initialNote) }
    val formScrollState = rememberScrollState()
    val hasMoreFields by remember { derivedStateOf { formScrollState.canScrollForward } }

    var selectedPackage by remember(packageTypes, initialPackageTypeId) {
        mutableStateOf(
            if (initialPackageTypeId != null) {
                packageTypes.firstOrNull { it.id == initialPackageTypeId } ?: packageTypes.firstOrNull()
            } else {
                packageTypes.firstOrNull { it.totalClasses == 4 && it.maxVisitsPerAttendance == 1 }
                    ?: packageTypes.firstOrNull()
            },
        )
    }

    var wantsFirstAttendance by remember {
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

    var selectedDate by remember(initialDate, earliestDate, schedules) {
        mutableStateOf(initialPackagePurchaseDate(initialDate, earliestDate, schedules))
    }

    val attendanceAlreadyRecorded = selectedDate?.let { it in recordedAttendanceDates } == true
    val recordFirstAttendance = wantsFirstAttendance && !attendanceAlreadyRecorded

    var showDatePicker by remember {
        mutableStateOf(false)
    }

    val packageExpirationDate =
        if (recordFirstAttendance) {
            selectedPackage?.validityWeeks?.let { weeks ->
                selectedDate?.plus(DatePeriod(days = weeks * 7))
            }
        } else {
            null
        }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = {
            Column {
                Text(title)
                if (hasMoreFields) {
                    Text(
                        text = "Scroll for more",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(formScrollState),
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
                                    text = stringResource(
                                        Res.string.package_valid_until,
                                        packageExpirationDate.toDisplayDate(),
                                        packageExpirationDate.year.toString(),
                                    ),
                                )
                            }
                        } else if (recordFirstAttendance &&
                            selectedPackage?.validityWeeks == null &&
                            selectedPackage != null
                        ) {
                            { Text(stringResource(Res.string.package_no_expiration)) }
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
                            enabled = !isSaving && !attendanceAlreadyRecorded,
                            role = Role.Checkbox,
                            onValueChange = {
                                wantsFirstAttendance = it
                            },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = recordFirstAttendance,
                        enabled = !isSaving && !attendanceAlreadyRecorded,
                        onCheckedChange = null,
                    )

                    Text(
                        text = "Record first attendance",
                        modifier = Modifier.padding(start = 12.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(
                            alpha = if (isSaving || attendanceAlreadyRecorded) 0.38f else 1f,
                        ),
                    )
                }

                if (attendanceAlreadyRecorded) {
                    Text(
                        "Attendance already recorded for this date. Renew without adding a visit",
                        modifier = Modifier.offset(y = (-2).dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.warning,
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
                        .padding(top = if (attendanceAlreadyRecorded) 12.dp else 8.dp)
                        .testTag("registration-note"),
                )

                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                enabled =
                !isSaving &&
                    canSubmit &&
                    selectedPackage != null &&
                    selectedDate?.let { isPackagePurchaseDateSelectable(it, initialDate, earliestDate, schedules) } ==
                    true &&
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
            remember(initialDate, earliestDate, schedules) {
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                        val date =
                            Instant
                                .fromEpochMilliseconds(utcTimeMillis)
                                .toLocalDateTime(TimeZone.UTC)
                                .date

                        return isPackagePurchaseDateSelectable(date, initialDate, earliestDate, schedules)
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

        ClassDatePickerDialog(
            title = datePickerTitle,
            state = datePickerState,
            canConfirm = datePickerState.selectedDateMillis?.let { selectableDates.isSelectableDate(it) } == true,
            availabilityKey = listOf(initialDate, earliestDate, schedules),
            onConfirm = {
                datePickerState.selectedDateMillis?.let { millis ->
                    selectedDate = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date
                }
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }
}

internal fun isPackagePurchaseDateSelectable(
    date: LocalDate,
    today: LocalDate,
    earliestDate: LocalDate?,
    schedules: List<ClassSchedule>? = null,
): Boolean = (
    if (schedules == null) {
        isAttendanceDateSelectable(date, today)
    } else {
        date <= today && schedules.isRegistrationClassDate(date)
    }
    ) &&
    (earliestDate == null || date >= earliestDate)

internal fun initialPackagePurchaseDate(
    today: LocalDate,
    earliestDate: LocalDate?,
    schedules: List<ClassSchedule>? = null,
): LocalDate? {
    val latestDate = if (schedules == null) {
        latestScheduledClassDate(today)
    } else {
        schedules.latestRegistrationClassDate(today)
    }
    return latestDate?.takeIf { isPackagePurchaseDateSelectable(it, today, earliestDate, schedules) }
}
