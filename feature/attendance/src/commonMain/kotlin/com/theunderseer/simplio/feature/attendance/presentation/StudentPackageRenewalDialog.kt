package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import com.theunderseer.simplio.feature.attendance.domain.model.ClassSchedule
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent
import com.theunderseer.simplio.feature.attendance.domain.model.StudentGroup
import kotlinx.datetime.LocalDate

@Composable
internal fun StudentPackageRenewalDialog(
    registeredStudent: RegisteredStudent,
    studentGroups: List<StudentGroup>,
    packageTypes: List<PackageType>,
    currentDate: LocalDate,
    isSaving: Boolean,
    errorMessage: String?,
    onRenew: (PackageType, LocalDate, Int, Boolean, String) -> Unit,
    onDismiss: () -> Unit,
    groupSchedules: Map<Int, List<ClassSchedule>> = emptyMap(),
) {
    val student = registeredStudent.student
    val previousPackage = registeredStudent.studentPackage
    key(student.id, previousPackage.id) {
        StudentPackageDialog(
            title = "Renew package for ${student.fullName}",
            confirmLabel = "Renew",
            dateLabel = "Purchase date",
            datePickerTitle = "Select purchase date",
            packageTypes = packageTypes,
            initialDate = currentDate,
            schedules = groupSchedules[student.groupId],
            initialPackageTypeId = previousPackage.packageType.id,
            earliestDate = previousPackage.earliestRenewalDate,
            recordedAttendanceDates = registeredStudent.recordedAttendanceDates,
            initialNote = student.note,
            isSaving = isSaving,
            errorMessage = errorMessage,
            studentFields = {
                studentGroups.firstOrNull { it.id == student.groupId }?.let { Text(it.name) }
            },
            onConfirm = onRenew,
            onDismiss = onDismiss,
        )
    }
}
