package com.theunderseer.simplio.feature.attendance.fixtures

import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent
import com.theunderseer.simplio.feature.attendance.domain.model.Student
import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage
import com.theunderseer.simplio.feature.attendance.presentation.AttendanceUiState
import kotlinx.datetime.LocalDate

internal fun testCompletedPackage(
    student: Student = AttendanceTestData.student,
    packageType: PackageType = AttendanceTestData.fourClassPackage,
    id: Int = 1,
): StudentPackage {
    val dates = listOf(
        AttendanceTestData.firstClassDate,
        AttendanceTestData.secondClassDate,
        AttendanceTestData.thirdClassDate,
        AttendanceTestData.fourthClassDate,
        AttendanceTestData.fifthClassDate,
        AttendanceTestData.sixthClassDate,
        AttendanceTestData.seventhClassDate,
        AttendanceTestData.eighthClassDate,
    )
    var remaining = packageType.totalClasses
    val visits = buildMap {
        for (date in dates) {
            if (remaining == 0) break
            val count = minOf(remaining, packageType.maxVisitsPerAttendance)
            put(date, count)
            remaining -= count
        }
    }
    check(remaining == 0)
    return testStudentPackage(id = id, studentId = student.id, packageType = packageType, visitsByDate = visits)
}

internal fun testPackageRenewalState(
    student: Student = AttendanceTestData.student,
    previousPackage: StudentPackage = testCompletedPackage(student),
    currentDate: LocalDate = AttendanceTestData.sixthClassDate,
): AttendanceUiState.Content = testAttendanceContentUiState(
    attendance = com.theunderseer.simplio.feature.attendance.domain.model.Attendance(
        entries = listOf(RegisteredStudent(student, previousPackage)),
    ),
    packageTypes = listOf(
        AttendanceTestData.fourClassPackage,
        AttendanceTestData.fiveClassPackage,
        AttendanceTestData.duoSpecialPackage,
    ),
    currentDate = currentDate,
)

internal data class StudentPackageRenewalSubmission(
    val studentId: Int,
    val previousPackageId: Int,
    val packageType: PackageType,
    val purchasedOn: LocalDate,
    val visitCount: Int,
    val recordFirstAttendance: Boolean,
    val note: String,
)

internal class StudentPackageRenewalTestCallback {
    var submission: StudentPackageRenewalSubmission? = null
        private set

    fun onRenew(
        studentId: Int,
        previousPackageId: Int,
        packageType: PackageType,
        purchasedOn: LocalDate,
        visitCount: Int,
        recordFirstAttendance: Boolean,
        note: String,
    ) {
        submission = StudentPackageRenewalSubmission(
            studentId,
            previousPackageId,
            packageType,
            purchasedOn,
            visitCount,
            recordFirstAttendance,
            note,
        )
    }
}
