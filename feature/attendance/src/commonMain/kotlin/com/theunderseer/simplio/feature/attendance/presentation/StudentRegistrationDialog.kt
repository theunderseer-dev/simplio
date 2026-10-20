package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.StudentGroup
import kotlinx.datetime.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentRegistrationDialog(
    studentGroups: List<StudentGroup>,
    packageTypes: List<PackageType>,
    initialDate: LocalDate,
    onRegister: (String, Int, PackageType, LocalDate, Int, Boolean, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var fullName by remember { mutableStateOf("") }
    var selectedGroupId by remember(studentGroups) { mutableStateOf<Int?>(null) }
    StudentPackageDialog(
        title = "Add Student",
        confirmLabel = "Register",
        dateLabel = "Registration date",
        datePickerTitle = "Select registration date",
        packageTypes = packageTypes,
        initialDate = initialDate,
        canSubmit = fullName.isNotBlank() && selectedGroupId != null,
        studentFields = {
            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("registration-full-name"),
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
        },
        onConfirm = { packageType, date, visits, recordAttendance, note ->
            onRegister(
                fullName.trim(),
                checkNotNull(selectedGroupId),
                packageType,
                date,
                visits,
                recordAttendance,
                note,
            )
        },
        onDismiss = onDismiss,
    )
}

internal fun isRegistrationDateSelectable(date: LocalDate, today: LocalDate): Boolean =
    isPackagePurchaseDateSelectable(date, today, earliestDate = null)
