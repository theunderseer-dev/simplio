package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.runtime.Composable
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.StudentGroup
import com.theunderseer.simplio.feature.attendance.fixtures.testClassPackage
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentGroups
import kotlinx.datetime.LocalDate

@Composable
internal fun StudentRegistrationDialogForTest(
    initialDate: LocalDate = LocalDate(2026, 10, 5),
    packageTypes: List<PackageType> = listOf(testClassPackage()),
    studentGroups: List<StudentGroup> = testStudentGroups(),
    onRegister: (
        fullName: String,
        groupId: Int,
        packageType: PackageType,
        registeredOn: LocalDate,
        visitCount: Int,
    ) -> Unit = { _, _, _, _, _ -> },
    onDismiss: () -> Unit = {},
) {
    StudentRegistrationDialog(
        studentGroups = studentGroups,
        packageTypes = packageTypes,
        initialDate = initialDate,
        onRegister = onRegister,
        onDismiss = onDismiss,
    )
}
