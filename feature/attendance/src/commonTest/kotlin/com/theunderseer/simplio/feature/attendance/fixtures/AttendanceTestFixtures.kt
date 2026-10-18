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
    val studentNote = "Return 100 THB next time"

    val student = testStudent()
    val studentEntity = testStudentEntity()
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
    val firstClassDate = registrationDate
    val secondClassDate = LocalDate(2026, 10, 5)
    val thirdClassDate = LocalDate(2026, 10, 9)
    val fourthClassDate = LocalDate(2026, 10, 12)
    val fifthClassDate = LocalDate(2026, 10, 16)
    val sixthClassDate = LocalDate(2026, 10, 19)
    val seventhClassDate = LocalDate(2026, 10, 23)
    val eighthClassDate = LocalDate(2026, 10, 26)

    val currentDate = LocalDate(2026, 10, 8)
}

fun testStudentGroups(): List<StudentGroup> = DefaultStudentGroups.ALL

fun testStudentGroupEntity(
    id: Int = DefaultStudentGroups.GROUP_A.id,
    name: String = DefaultStudentGroups.GROUP_A.name,
): StudentGroupEntity = StudentGroupEntity(
    id = id,
    name = name,
)

fun testStudent(id: Int = 1, fullName: String = "Anna Smith", groupId: Int = 1,
    note: String = "",
): Student = Student(
    id = id,
    fullName = fullName,
    groupId = groupId,
    note = note,
)

fun testStudentEntity(id: Int = 0, fullName: String = "Anna Smith", groupId: Int = 1,
    note: String = "",
): StudentEntity = StudentEntity(
    id = id,
    fullName = fullName,
    groupId = groupId,
    note = note,
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

internal fun testStudentPackage(
    id: Int = 1,
    studentId: Int = AttendanceTestData.student.id,
    packageType: PackageType = AttendanceTestData.fourClassPackage,
    startedOn: LocalDate? = AttendanceTestData.firstClassDate,
    purchasedOn: LocalDate = startedOn ?: AttendanceTestData.registrationDate,
    visitsByDate: Map<LocalDate, Int> =
        startedOn?.let { mapOf(it to 1) } ?: emptyMap(),
): StudentPackage = StudentPackage(
    id = id,
    studentId = studentId,
    packageType = packageType,
    purchasedOn = purchasedOn,
    startedOn = startedOn,
    attendanceHistory = PackageAttendanceHistory(
        visitsByDate = visitsByDate,
    ),
)

internal fun testStudentPackageEntity(
    studentId: Int,
    packageType: PackageType,
    startedOnEpochDay: Long?,
    id: Int = 0,
    purchasedOnEpochDay: Long =
        startedOnEpochDay ?: AttendanceTestData.registrationDate.toEpochDays(),
): StudentPackageEntity = StudentPackageEntity(
    id = id,
    studentId = studentId,
    packageTypeId = packageType.id,
    packageName = packageType.name,
    totalClasses = packageType.totalClasses,
    validityWeeks = packageType.validityWeeks,
    price = packageType.price,
    purchasedOnEpochDay = purchasedOnEpochDay,
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
    startedOn: LocalDate? = AttendanceTestData.firstClassDate,
    visitsByDate: Map<LocalDate, Int> =
        startedOn?.let { mapOf(it to 1) } ?: emptyMap(),
    currentDate: LocalDate = AttendanceTestData.currentDate,
    purchasedOn: LocalDate = startedOn ?: AttendanceTestData.registrationDate,
): AttendanceUiState.Content = testAttendanceContentUiState(
    attendance = Attendance(
        entries = listOf(
            RegisteredStudent(
                student = student,
                studentPackage = testStudentPackage(
                    studentId = student.id,
                    packageType = packageType,
                    purchasedOn = purchasedOn,
                    startedOn = startedOn,
                    visitsByDate = visitsByDate,
                ),
            ),
        ),
    ),
    packageTypes = listOf(packageType),
    currentDate = currentDate,
)

internal data class StudentRegistrationSubmission(
    val fullName: String,
    val groupId: Int,
    val packageType: PackageType,
    val registeredOn: LocalDate,
    val visitCount: Int,
    val recordFirstAttendance: Boolean,
    val note: String = "",
)

internal class StudentRegistrationTestCallback {
    var submission: StudentRegistrationSubmission? = null
        private set

    fun onRegister(
        fullName: String,
        groupId: Int,
        packageType: PackageType,
        registeredOn: LocalDate,
        visitCount: Int,
        recordFirstAttendance: Boolean,
    note: String,
) {
        submission = StudentRegistrationSubmission(
            fullName = fullName,
            groupId = groupId,
            packageType = packageType,
            registeredOn = registeredOn,
            visitCount = visitCount,
            recordFirstAttendance = recordFirstAttendance,
            note = note,
        )
    }
}
