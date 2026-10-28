package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.theunderseer.simplio.feature.attendance.domain.model.ClassSchedule
import com.theunderseer.simplio.feature.attendance.domain.model.MAX_STUDENT_NAME_LENGTH
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.StudentGroup
import com.theunderseer.simplio.feature.attendance.domain.model.isValidStudentName
import com.theunderseer.simplio.feature.attendance.resources.Res
import com.theunderseer.simplio.feature.attendance.resources.student_name_invalid
import com.theunderseer.simplio.shared.presentation.components.SimplioFilterChip
import com.theunderseer.simplio.shared.presentation.input.ValidatedOutlinedTextField
import com.theunderseer.simplio.shared.presentation.input.rememberValidatedInput
import com.theunderseer.simplio.shared.validation.InputPolicy
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentRegistrationDialog(
    studentGroups: List<StudentGroup>,
    packageTypes: List<PackageType>,
    initialDate: LocalDate,
    onRegister: (String, Int, PackageType, LocalDate, Int, Boolean, String) -> Unit,
    onDismiss: () -> Unit,
    groupSchedules: Map<Int, List<ClassSchedule>> = emptyMap(),
    groupCurrentDates: Map<Int, LocalDate> = emptyMap(),
    fixedDate: LocalDate? = null,
    initialGroupId: Int? = null,
    isSaving: Boolean = false,
    errorMessage: String? = null,
    visible: Boolean = true,
    preferredPackageTypeId: Int? = null,
    onCreatePackage: (() -> Unit)? = null,
) {
    val nameInput = rememberValidatedInput("", InputPolicy.SingleLine(MAX_STUDENT_NAME_LENGTH))
    val fullName = nameInput.text
    val invalidName = fullName.isNotEmpty() && !isValidStudentName(fullName)
    var selectedGroupId by remember { mutableStateOf(initialGroupId) }
    StudentPackageDialog(
        title = "Add Student",
        fixedDate = fixedDate,
        visible = visible,
        preferredPackageTypeId = preferredPackageTypeId,
        onCreatePackage = onCreatePackage,
        isSaving = isSaving,
        errorMessage = errorMessage,
        confirmLabel = "Register",
        dateLabel = "Registration date",
        datePickerTitle = "Select registration date",
        packageTypes = packageTypes,
        initialDate = groupCurrentDates[selectedGroupId] ?: initialDate,
        canSubmit =
        isValidStudentName(fullName) && !nameInput.rejectedEdit && studentGroups.any { it.id == selectedGroupId },
        schedules = groupSchedules[selectedGroupId],
        studentFields = {
            ValidatedOutlinedTextField(
                enabled = !isSaving,
                input = nameInput,
                isError = invalidName,
                supportingText = if (invalidName) {
                    { Text(stringResource(Res.string.student_name_invalid)) }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth().testTag("registration-full-name"),
                label = {
                    Text("Full name")
                },
            )

            Column(
                modifier = Modifier.padding(vertical = 12.dp),
            ) {
                Text("Group")

                FlowRow(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    studentGroups.forEach { group ->
                        SimplioFilterChip(
                            enabled = !isSaving,
                            selected = selectedGroupId == group.id,
                            onClick = {
                                selectedGroupId = group.id
                            },
                            label = { Text(group.name) },
                        )
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
