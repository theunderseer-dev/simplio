package com.theunderseer.simplio.feature.attendance.fixtures

import com.theunderseer.simplio.feature.attendance.data.local.entity.PackageTypeEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentGroupEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentPackageEntity
import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.ClassHistoryEntry
import com.theunderseer.simplio.feature.attendance.domain.model.ClassHistoryStudent
import com.theunderseer.simplio.feature.attendance.domain.model.DefaultStudentGroups
import com.theunderseer.simplio.feature.attendance.domain.model.PackageAttendanceHistory
import com.theunderseer.simplio.feature.attendance.domain.model.PackagePlan
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent
import com.theunderseer.simplio.feature.attendance.domain.model.Student
import com.theunderseer.simplio.feature.attendance.domain.model.StudentGroup
import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage
import com.theunderseer.simplio.feature.attendance.presentation.AttendanceUiState
import com.theunderseer.simplio.feature.attendance.presentation.attendanceContentUiState
import kotlinx.datetime.LocalDate

internal object AttendanceTestData {
    val student = testStudent()
    val studentGroups = testStudentGroups()

    val fourClassPackage = testClassPackage()

    val fiveClassPackage = testClassPackage(
        id = 2,
        plan = PackagePlan.FIVE_CLASSES,
    )

    val duoSpecialPackage = testClassPackage(
        id = 3,
        plan = PackagePlan.DUO_SPECIAL,
    )

    val availablePackages = listOf(
        fourClassPackage,
        fiveClassPackage,
    )

    val registrationDate = LocalDate(2026, 10, 2)
    val firstAttendanceDate = registrationDate
    val nextAttendanceDate = LocalDate(2026, 10, 5)

    val currentDate = LocalDate(2026, 10, 8)
    val nextClassDate = LocalDate(2026, 10, 9)
}

fun testStudentGroups(): List<StudentGroup> = DefaultStudentGroups.ALL

fun testStudentGroupEntity(
    id: Int = DefaultStudentGroups.GROUP_A.id,
    name: String = DefaultStudentGroups.GROUP_A.name,
): StudentGroupEntity = StudentGroupEntity(
    id = id,
    name = name,
)

fun testStudent(id: Int = 1, fullName: String = "Anna Smith", groupId: Int = 1): Student = Student(
    id = id,
    fullName = fullName,
    groupId = groupId,
)

fun testStudentEntity(id: Int = 0, fullName: String = "Anna Smith", groupId: Int = 1): StudentEntity = StudentEntity(
    id = id,
    fullName = fullName,
    groupId = groupId,
)

internal fun testClassPackage(id: Int = 1, plan: PackagePlan = PackagePlan.FOUR_CLASSES): PackageType =
    plan.toPackageType(id)

internal fun testPackageTypeEntity(packageType: PackageType, id: Int = 0): PackageTypeEntity = PackageTypeEntity(
    id = id,
    name = packageType.name,
    totalClasses = packageType.totalClasses,
    validityWeeks = packageType.validityWeeks,
    price = packageType.price,
    maxVisitsPerAttendance = packageType.maxVisitsPerAttendance,
)

internal fun testStudentPackageEntity(
    studentId: Int,
    packageType: PackageType,
    startedOnEpochDay: Long,
    id: Int = 0,
): StudentPackageEntity = StudentPackageEntity(
    id = id,
    studentId = studentId,
    packageTypeId = packageType.id,
    packageName = packageType.name,
    totalClasses = packageType.totalClasses,
    validityWeeks = packageType.validityWeeks,
    price = packageType.price,
    startedOnEpochDay = startedOnEpochDay,
    maxVisitsPerAttendance = packageType.maxVisitsPerAttendance,
)

internal fun testAttendanceContentUiState(
    attendance: Attendance,
    packageTypes: List<PackageType>,
    currentDate: LocalDate,
    studentGroups: List<StudentGroup> = testStudentGroups(),
): AttendanceUiState.Content = attendanceContentUiState(
    attendance = attendance,
    packageTypes = packageTypes,
    currentDate = currentDate,
).copy(
    studentGroups = studentGroups,
)

internal fun testClassHistoryEntry(
    date: LocalDate,
    students: List<ClassHistoryStudent> = emptyList(),
): ClassHistoryEntry = ClassHistoryEntry(
    date = date,
    students = students,
)

internal fun testStudentAttendanceUiState(
    student: Student = AttendanceTestData.student,
    packageType: PackageType = AttendanceTestData.fourClassPackage,
    startedOn: LocalDate = AttendanceTestData.firstAttendanceDate,
    visitsByDate: Map<LocalDate, Int> = mapOf(startedOn to 1),
    currentDate: LocalDate = AttendanceTestData.currentDate,
): AttendanceUiState.Content = testAttendanceContentUiState(
    attendance = Attendance(
        entries = listOf(
            RegisteredStudent(
                student = student,
                studentPackage = StudentPackage(
                    id = 1,
                    studentId = student.id,
                    packageType = packageType,
                    startedOn = startedOn,
                    attendanceHistory = PackageAttendanceHistory(
                        visitsByDate = visitsByDate,
                    ),
                ),
            ),
        ),
    ),
    packageTypes = listOf(packageType),
    currentDate = currentDate,
)
