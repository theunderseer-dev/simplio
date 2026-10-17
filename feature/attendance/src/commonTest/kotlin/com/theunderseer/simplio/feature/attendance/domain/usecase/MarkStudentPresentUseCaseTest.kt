package com.theunderseer.simplio.feature.attendance.domain.usecase

import com.theunderseer.simplio.feature.attendance.domain.model.PackageAttendanceHistory
import com.theunderseer.simplio.feature.attendance.domain.result.MarkStudentPresentResult
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeTransactionRunner
import com.theunderseer.simplio.feature.attendance.fake.RecordedAttendance
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.duoSpecialPackage
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.eighthClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.fifthClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.firstClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.fiveClassPackage
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.fourthClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.registrationDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.secondClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.seventhClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.sixthClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.thirdClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentPackage
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MarkStudentPresentUseCaseTest {
    private lateinit var repository: FakeStudentPackageRepository
    private lateinit var markStudentPresent: MarkStudentPresentUseCase
    private lateinit var transactionRunner: FakeTransactionRunner
    private val studentId = AttendanceTestData.student.id

    @BeforeTest
    fun setUp() {
        repository = FakeStudentPackageRepository()
        transactionRunner = FakeTransactionRunner()
        markStudentPresent =
            MarkStudentPresentUseCase(
                repository = repository,
                transactionRunner = transactionRunner,
            )
    }

    @Test
    fun `marking a student as present adds the date to attendance`() = runTest {
        // Given
        // student has an active 5-class package
        // And
        // he has already attended one class
        val studentPackage =
            repository.seed(
                studentId = studentId,
                packageType = fiveClassPackage,
                startedOn = firstClassDate,
            )

        // When
        // I mark student present on a new date
        val result =
            markStudentPresent(
                studentId = studentId,
                attendanceDate = secondClassDate,
            )

        // Then
        // the operation is successful
        val success = assertIs<MarkStudentPresentResult.Success>(result)

        // And
        // the same package is updated, not replaced
        assertEquals(
            studentPackage.id,
            success.studentPackage.id,
        )

        // And
        // the new attendance date is recorded
        assertEquals(
            setOf(firstClassDate, secondClassDate),
            success.studentPackage.attendanceDates,
        )

        // And
        // used classes increase from 1 to 2
        assertEquals(2, success.studentPackage.usedClasses)

        // And
        // the updated package is persisted
        assertEquals(
            success.studentPackage,
            repository.getLatestPackageForStudent(studentId),
        )

        // And
        // the attendance persistence operation is recorded
        assertEquals(
            listOf(
                RecordedAttendance(
                    packageId = studentPackage.id,
                    attendedOn = secondClassDate,
                ),
            ),
            repository.recordedAttendances,
        )
    }

    @Test
    fun `marking a student as present on the same date does not duplicate attendance`() = runTest {
        // Given
        // student has an active 5-class package
        // And
        // he has already attended one class
        repository.seed(
            studentId = studentId,
            packageType = fiveClassPackage,
            startedOn = registrationDate,
        )

        // When
        // I mark the student present on the same date again
        val result =
            markStudentPresent(
                studentId = studentId,
                attendanceDate = registrationDate,
            )

        // Then
        // the operation is successful
        val success = assertIs<MarkStudentPresentResult.Success>(result)

        // And
        // the attendance date is not duplicated
        assertEquals(
            setOf(registrationDate),
            success.studentPackage.attendanceDates,
        )

        // And
        // used classes remain at 1
        assertEquals(1, success.studentPackage.usedClasses)

        // And
        // no persistence update is required
        assertTrue(repository.recordedAttendances.isEmpty())
    }

    @Test
    fun `marking the final attendance marks the package as completed`() = runTest {
        // Given
        // student has a 5-class package
        // And
        // he has already used 4 classes
        val previousDates = setOf(
            firstClassDate,
            secondClassDate,
            thirdClassDate,
            fourthClassDate,
        )
        val finalAttendanceDate = fifthClassDate
        val studentPackage = repository.seed(
            studentId = studentId,
            packageType = fiveClassPackage,
            startedOn = registrationDate,
            attendanceDates = previousDates,
        )

        // When
        // I mark the student present on a new date
        val result =
            markStudentPresent(
                studentId = studentId,
                attendanceDate = finalAttendanceDate,
            )

        // Then
        // the operation is successful
        val success = assertIs<MarkStudentPresentResult.Success>(result)

        // And
        // the new attendance date is recorded
        assertEquals(
            previousDates + finalAttendanceDate,
            success.studentPackage.attendanceDates,
        )

        // And
        // used classes become 5 of 5
        assertEquals(5, success.studentPackage.usedClasses)

        // And
        // the package is marked as completed
        assertTrue(success.studentPackage.isCompleted)

        // And
        // same package was updated, not replaced
        assertEquals(
            studentPackage.id,
            success.studentPackage.id,
        )

        // And
        // completed state is persisted
        assertEquals(
            success.studentPackage,
            repository.getLatestPackageForStudent(studentId),
        )

        // And
        // the final attendance persistence operation is recorded
        assertEquals(
            listOf(
                RecordedAttendance(
                    packageId = studentPackage.id,
                    attendedOn = finalAttendanceDate,
                ),
            ),
            repository.recordedAttendances,
        )
    }

    @Test
    fun `marking attendance on a completed package is rejected`() = runTest {
        // Given
        // student has a completed 5-class package
        // And
        // all 5 classes have already been used
        val studentPackage = repository.seed(
            studentId = studentId,
            packageType = fiveClassPackage,
            startedOn = registrationDate,
            attendanceDates = setOf(
                firstClassDate,
                secondClassDate,
                thirdClassDate,
                fourthClassDate,
                fifthClassDate,
            ),
        )
        val nextAttendanceDate = sixthClassDate

        assertTrue(studentPackage.isCompleted)
        assertFalse(studentPackage.isExpiredOn(nextAttendanceDate))

        // When
        // I try to mark the student present on another date
        val result =
            markStudentPresent(
                studentId = studentId,
                attendanceDate = nextAttendanceDate,
            )

        // Then
        // the operation reports that the package is completed
        val completedResult = assertIs<MarkStudentPresentResult.PackageCompleted>(result)

        // And
        // the original package is returned
        assertEquals(
            studentPackage,
            completedResult.studentPackage,
        )

        // And
        // the attendance must not be added
        assertFalse(nextAttendanceDate in completedResult.studentPackage.attendanceDates)

        // And
        // rejected attendance is not persisted
        assertTrue(repository.recordedAttendances.isEmpty())
    }

    @Test
    fun `marking attendance on an expired package is rejected`() = runTest {
        // Given
        // student has a 5-class package
        // And
        // the package still has unused classes
        // And
        // the package has expired
        val expiredPackageStartDate = LocalDate(2026, 8, 1)
        val previousAttendanceDate = LocalDate(2026, 8, 8)
        val attendanceDate = secondClassDate

        val studentPackage = repository.seed(
            studentId = studentId,
            packageType = fiveClassPackage,
            startedOn = expiredPackageStartDate,
            attendanceDates = setOf(
                expiredPackageStartDate,
                previousAttendanceDate,
            ),
        )

        assertFalse(studentPackage.isCompleted)
        assertTrue(studentPackage.isExpiredOn(attendanceDate))

        // When
        // I try to mark the student present on a later date
        val result =
            markStudentPresent(
                studentId = studentId,
                attendanceDate = attendanceDate,
            )

        // Then
        // the package is already expired
        val expiredResult = assertIs<MarkStudentPresentResult.PackageExpired>(result)

        // And
        // the original package is returned
        assertEquals(
            studentPackage,
            expiredResult.studentPackage,
        )

        // And
        // the attendance must not be added
        assertFalse(attendanceDate in expiredResult.studentPackage.attendanceDates)

        // And
        // rejected attendance is not persisted
        assertTrue(repository.recordedAttendances.isEmpty())
    }

    @Test
    fun `marking attendance without a student package is rejected`() = runTest {
        // Given
        // student has no package at all
        // When
        // I try to mark student present on a date without any package
        val result = markStudentPresent(
            studentId = studentId,
            attendanceDate = secondClassDate,
        )

        // Then
        // the operation reports that no package was found for the student
        assertIs<MarkStudentPresentResult.PackageNotFound>(result)

        // And
        // rejected attendance is not persisted
        assertTrue(repository.recordedAttendances.isEmpty())
    }

    @Test
    fun `attendance marking executes within a single transaction`() = runTest {
        // Given
        repository.seed(
            studentId = studentId,
            packageType = fiveClassPackage,
            startedOn = registrationDate,
        )

        // When
        markStudentPresent(
            studentId = studentId,
            attendanceDate = secondClassDate,
        )

        // Then
        assertEquals(
            1,
            transactionRunner.runCount,
        )
    }

    @Test
    fun `marking duo attendance records two visits for selected date`() = runTest {
        // Given

        val studentPackage =
            repository.seed(
                studentId = studentId,
                packageType = duoSpecialPackage,
                startedOn = firstClassDate,
            )

        // When
        val result =
            markStudentPresent(
                studentId = studentId,
                attendanceDate = secondClassDate,
                visitCount = 2,
            )

        // Then
        val success =
            assertIs<MarkStudentPresentResult.Success>(result)

        val expectedPackage =
            studentPackage.copy(
                attendanceHistory =
                PackageAttendanceHistory(
                    visitsByDate =
                    mapOf(
                        firstClassDate to 1,
                        secondClassDate to 2,
                    ),
                ),
            )

        assertEquals(
            expectedPackage,
            success.studentPackage,
        )

        assertEquals(
            3,
            success.studentPackage.usedClasses,
        )

        assertEquals(
            expectedPackage,
            repository.getLatestPackageForStudent(studentId),
        )

        assertEquals(
            listOf(
                RecordedAttendance(
                    packageId = studentPackage.id,
                    attendedOn = secondClassDate,
                    visitCount = 2,
                ),
            ),
            repository.recordedAttendances,
        )
    }

    @Test
    fun `marking two visits for individual package is rejected without changes`() = runTest {
        // Given
        val studentPackage =
            repository.seed(
                studentId = studentId,
                packageType = fiveClassPackage,
                startedOn = firstClassDate,
            )

        assertEquals(
            4,
            studentPackage.remainingClasses,
        )

        // When
        val result =
            markStudentPresent(
                studentId = studentId,
                attendanceDate = secondClassDate,
                visitCount = 2,
            )

        // Then
        assertIs<MarkStudentPresentResult.InvalidVisitCount>(result)

        assertEquals(
            studentPackage,
            repository.getLatestPackageForStudent(studentId),
        )

        assertTrue(
            repository.recordedAttendances.isEmpty(),
        )
    }

    @Test
    fun `marking two visits when only one remains is rejected without changes`() = runTest {
        // Given
        val studentPackage = repository.seed(
            studentId = studentId,
            packageType = duoSpecialPackage,
            startedOn = firstClassDate,
            attendanceDates = setOf(
                firstClassDate,
                secondClassDate,
                thirdClassDate,
                fourthClassDate,
                fifthClassDate,
                sixthClassDate,
                seventhClassDate,
            ),
        )

        assertEquals(
            1,
            studentPackage.remainingClasses,
        )

        // When
        val result = markStudentPresent(
            studentId = studentId,
            attendanceDate = eighthClassDate,
            visitCount = 2,
        )

        // Then
        assertIs<MarkStudentPresentResult.InsufficientRemainingVisits>(result)

        assertEquals(
            studentPackage,
            repository.getLatestPackageForStudent(studentId),
        )

        assertTrue(
            repository.recordedAttendances.isEmpty(),
        )
    }

    @Test
    fun `marking one remaining visit completes duo package`() = runTest {
        // Given
        val finalAttendance = eighthClassDate

        val previousDates = setOf(
            firstClassDate,
            secondClassDate,
            thirdClassDate,
            fourthClassDate,
            fifthClassDate,
            sixthClassDate,
            seventhClassDate,
        )

        val studentPackage = repository.seed(
            studentId = studentId,
            packageType = duoSpecialPackage,
            startedOn = firstClassDate,
            attendanceDates = previousDates,
        )

        assertEquals(1, studentPackage.remainingClasses)
        assertFalse(studentPackage.isCompleted)

        // When
        val result =
            markStudentPresent(
                studentId = studentId,
                attendanceDate = finalAttendance,
                visitCount = 1,
            )

        // Then
        val success =
            assertIs<MarkStudentPresentResult.Success>(result)

        val expectedPackage =
            studentPackage.copy(
                attendanceHistory =
                PackageAttendanceHistory(
                    visitsByDate =
                    (previousDates + finalAttendance)
                        .associateWith { 1 },
                ),
            )

        assertEquals(expectedPackage, success.studentPackage)
        assertEquals(8, success.studentPackage.usedClasses)
        assertEquals(0, success.studentPackage.remainingClasses)
        assertTrue(success.studentPackage.isCompleted)

        assertEquals(
            expectedPackage,
            repository.getLatestPackageForStudent(studentId),
        )

        assertEquals(
            listOf(
                RecordedAttendance(
                    packageId = studentPackage.id,
                    attendedOn = finalAttendance,
                    visitCount = 1,
                ),
            ),
            repository.recordedAttendances,
        )
    }

    @Test
    fun `repeating duo attendance preserves two visits without another repository write`() = runTest {
        // Given
        val studentPackage =
            repository.seed(
                studentId = studentId,
                packageType = duoSpecialPackage,
                startedOn = firstClassDate,
            )

        repository.recordAttendance(
            packageId = studentPackage.id,
            attendedOn = secondClassDate,
            visitCount = 2,
        )

        val expectedPackage =
            studentPackage.copy(
                attendanceHistory =
                PackageAttendanceHistory(
                    visitsByDate =
                    mapOf(
                        firstClassDate to 1,
                        secondClassDate to 2,
                    ),
                ),
            )

        // When
        val result =
            markStudentPresent(
                studentId = studentId,
                attendanceDate = secondClassDate,
                visitCount = 1,
            )

        // Then
        val success =
            assertIs<MarkStudentPresentResult.Success>(result)

        assertEquals(expectedPackage, success.studentPackage)
        assertEquals(3, success.studentPackage.usedClasses)

        assertEquals(
            expectedPackage,
            repository.getLatestPackageForStudent(studentId),
        )

        assertEquals(
            listOf(
                RecordedAttendance(
                    packageId = studentPackage.id,
                    attendedOn = secondClassDate,
                    visitCount = 2,
                ),
            ),
            repository.recordedAttendances,
        )
    }

    @Test
    fun `rejects attendance before purchase and preserves prepaid package`() = runTest {
        // Given
        val studentPackage = testStudentPackage(
            purchasedOn = secondClassDate,
            startedOn = null,
        )
        val repository = FakeStudentPackageRepository(
            initialPackages = listOf(studentPackage),
        )
        val markStudentPresent = MarkStudentPresentUseCase(
            repository = repository,
            transactionRunner = FakeTransactionRunner(),
        )

        // When
        val result = markStudentPresent(
            studentId = studentPackage.studentId,
            attendanceDate = firstClassDate,
        )

        // Then
        assertEquals(
            MarkStudentPresentResult.AttendanceDateTooEarly(
                earliestAllowedDate = secondClassDate,
            ),
            result,
        )
        assertEquals(
            studentPackage,
            repository.getLatestPackageForStudent(studentPackage.studentId),
        )
        assertTrue(repository.recordedAttendances.isEmpty())
    }

    @Test
    fun `rejects attendance before activation even when package was already purchased`() = runTest {
        // Given
        val studentPackage = testStudentPackage(
            purchasedOn = firstClassDate,
            startedOn = thirdClassDate,
        )
        val repository = FakeStudentPackageRepository(
            initialPackages = listOf(studentPackage),
        )
        val markStudentPresent = MarkStudentPresentUseCase(
            repository = repository,
            transactionRunner = FakeTransactionRunner(),
        )

        // When
        val result = markStudentPresent(
            studentId = studentPackage.studentId,
            attendanceDate = secondClassDate,
        )

        // Then
        assertEquals(
            MarkStudentPresentResult.AttendanceDateTooEarly(
                earliestAllowedDate = thirdClassDate,
            ),
            result,
        )
        assertEquals(
            studentPackage,
            repository.getLatestPackageForStudent(studentPackage.studentId),
        )
        assertTrue(repository.recordedAttendances.isEmpty())
    }

    @Test
    fun `first attendance on purchase date activates prepaid package`() = runTest {
        // Given
        val studentPackage = testStudentPackage(
            purchasedOn = firstClassDate,
            startedOn = null,
        )
        val repository = FakeStudentPackageRepository(
            initialPackages = listOf(studentPackage),
        )
        val markStudentPresent = MarkStudentPresentUseCase(
            repository = repository,
            transactionRunner = FakeTransactionRunner(),
        )

        // When
        val result = markStudentPresent(
            studentId = studentPackage.studentId,
            attendanceDate = firstClassDate,
        )

        // Then
        val success = assertIs<MarkStudentPresentResult.Success>(result)

        assertEquals(firstClassDate, success.studentPackage.purchasedOn)
        assertEquals(firstClassDate, success.studentPackage.startedOn)
        assertEquals(
            mapOf(firstClassDate to 1),
            success.studentPackage.attendanceHistory.visitsByDate,
        )
        assertEquals(
            success.studentPackage,
            repository.getLatestPackageForStudent(studentPackage.studentId),
        )
    }
}
