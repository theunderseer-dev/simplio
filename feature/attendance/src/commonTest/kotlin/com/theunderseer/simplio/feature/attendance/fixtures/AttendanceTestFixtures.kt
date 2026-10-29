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
import kotlin.time.Instant

internal object AttendanceTestData {
    val studentWithCompanionName = "Анна + 1 · ครู"
    val unrestrictedNote = "Give 20 ฿ change + 1 bonus 😊 — นัดหมาย\nNext time: 09:00!"
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

    val privateEightClassPackage = PackageType(
        id = 4,
        name = "Private · 8 classes · 2/week",
        totalClasses = 8,
        validityWeeks = null,
        price = 9000,
        maxVisitsPerAttendance = 1,
    )
    val privateThreeWeeklyPackage = privateEightClassPackage.copy(
        id = 5,
        name = "Private · 8 classes · 3/week",
        price = 8000,
    )
    val privateTwelveClassPackage = privateEightClassPackage.copy(
        id = 6,
        name = "Private · 12 classes · 3/week",
        totalClasses = 12,
        price = 12000,
    )
    val privateTwentyClassPackage = privateEightClassPackage.copy(
        id = 7,
        name = "Private · 20 classes · 3/week",
        totalClasses = 20,
        price = 22000,
    )
    val privatePackages = listOf(
        privateEightClassPackage,
        privateThreeWeeklyPackage,
        privateTwelveClassPackage,
        privateTwentyClassPackage,
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

    val class9Date = LocalDate(2026, 10, 30)
    val class10Date = LocalDate(2026, 11, 2)
    val class11Date = LocalDate(2026, 11, 6)
    val class12Date = LocalDate(2026, 11, 9)
    val class13Date = LocalDate(2026, 11, 13)
    val class14Date = LocalDate(2026, 11, 16)
    val class15Date = LocalDate(2026, 11, 20)
    val class16Date = LocalDate(2026, 11, 23)
    val class17Date = LocalDate(2026, 11, 27)
    val class18Date = LocalDate(2026, 11, 30)
    val class19Date = LocalDate(2026, 12, 4)
    val class20Date = LocalDate(2026, 12, 7)
    val packageClassDates = listOf(
        firstClassDate, secondClassDate, thirdClassDate, fourthClassDate,
        fifthClassDate, sixthClassDate, seventhClassDate, eighthClassDate,
        class9Date, class10Date, class11Date, class12Date, class13Date, class14Date,
        class15Date, class16Date, class17Date, class18Date, class19Date, class20Date,
    )

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

fun testStudent(id: Int = 1, fullName: String = "Anna Smith", groupId: Int = 1, note: String = ""): Student = Student(
    id = id,
    fullName = fullName,
    groupId = groupId,
    note = note,
)

fun testStudentEntity(
    id: Int = 0,
    fullName: String = "Anna Smith",
    groupId: Int = 1,
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
    validityExtensions: Map<String, Int> = emptyMap(),
): StudentPackage = StudentPackage(
    id = id,
    studentId = studentId,
    packageType = packageType,
    purchasedOn = purchasedOn,
    startedOn = startedOn,
    attendanceHistory = PackageAttendanceHistory(
        visitsByDate = visitsByDate,
    ),
    validityExtensions = validityExtensions,
)

internal fun testStudentPackageEntity(
    studentId: Int,
    packageType: PackageType,
    startedOnEpochDay: Long?,
    id: Int = 0,
    purchasedOnEpochDay: Long =
        startedOnEpochDay ?: AttendanceTestData.registrationDate.toEpochDays(),
    validityExtensions: Map<String, Int> = emptyMap(),
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
    validityExtensions = validityExtensions,
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
    sessionId: String? = null,
    timeLabel: String? = null,
    startsAt: Instant? = null,
    endsAt: Instant? = null,
): ClassHistoryEntry = ClassHistoryEntry(
    date = date,
    students = students,
    sessionId = sessionId,
    timeLabel = timeLabel,
    startsAt = startsAt,
    endsAt = endsAt,
)

internal fun testStudentAttendanceUiState(
    student: Student = AttendanceTestData.student,
    packageType: PackageType = AttendanceTestData.fourClassPackage,
    startedOn: LocalDate? = AttendanceTestData.firstClassDate,
    visitsByDate: Map<LocalDate, Int> =
        startedOn?.let { mapOf(it to 1) } ?: emptyMap(),
    currentDate: LocalDate = AttendanceTestData.currentDate,
    purchasedOn: LocalDate = startedOn ?: AttendanceTestData.registrationDate,
    previousAttendanceDates: Set<LocalDate> = emptySet(),
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
                previousAttendanceDates = previousAttendanceDates,
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
