package com.theunderseer.simplio.feature.attendance.presentation.dialog

import androidx.compose.runtime.Composable
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.StudentGroup
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.testClassPackage
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentGroups
import com.theunderseer.simplio.feature.attendance.presentation.StudentRegistrationDialog
import kotlinx.datetime.LocalDate

@Composable
internal fun StudentRegistrationDialogForTest(
    initialDate: LocalDate = AttendanceTestData.registrationDate,
    packageTypes: List<PackageType> = listOf(testClassPackage()),
    studentGroups: List<StudentGroup> = testStudentGroups(),
    onRegister: (String, Int, PackageType, LocalDate, Int, Boolean, String) -> Unit = { _, _, _, _, _, _, _ -> },
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
