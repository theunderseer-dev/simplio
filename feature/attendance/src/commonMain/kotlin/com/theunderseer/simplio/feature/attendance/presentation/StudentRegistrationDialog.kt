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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.StudentGroup
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentRegistrationDialog(
    studentGroups: List<StudentGroup>,
    packageTypes: List<PackageType>,
    initialDate: LocalDate,
    onRegister: (String, Int, PackageType, LocalDate, Int, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var fullName by remember {
        mutableStateOf("")
    }

    var selectedGroupId by remember(studentGroups) {
        mutableStateOf<Int?>(null)
    }

    var selectedPackage by remember {
        mutableStateOf(
            packageTypes.firstOrNull {
                it.totalClasses == 4 &&
                    it.maxVisitsPerAttendance == 1
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
            if (isPairedPackage) 2 else 1,
        )
    }

    var isPackageMenuExpanded by remember {
        mutableStateOf(false)
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
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = {
                        Text("Full name")
                    },
                )

                Column(
                    modifier = Modifier.padding(vertical = 12.dp),
                ) {
                    Text("Group")

                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        studentGroups.forEachIndexed { index, group ->
                            SegmentedButton(
                                selected = selectedGroupId == group.id,
                                onClick = {
                                    selectedGroupId = group.id
                                },
                                shape =
                                SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = studentGroups.size,
                                ),
                            ) {
                                Text(group.name)
                            }
                        }
                    }
                }

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

                ExposedDropdownMenuBox(
                    expanded = isPackageMenuExpanded,
                    onExpandedChange = {
                        isPackageMenuExpanded = it
                    },
                ) {
                    OutlinedTextField(
                        value = selectedPackage?.name.orEmpty(),
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = {
                            Text("Package")
                        },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(
                                expanded = isPackageMenuExpanded,
                            )
                        },
                        modifier =
                        Modifier.menuAnchor(
                            type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                            enabled = packageTypes.isNotEmpty(),
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
            }
        },
        confirmButton = {
            Button(
                enabled =
                fullName.isNotBlank() &&
                    selectedGroupId != null &&
                    selectedPackage != null,
                onClick = {
                    onRegister(
                        fullName.trim(),
                        checkNotNull(selectedGroupId),
                        checkNotNull(selectedPackage),
                        selectedDate,
                        selectedVisitCount,
                        recordFirstAttendance,
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
