package com.theunderseer.simplio.feature.attendance.domain.usecase

import com.theunderseer.simplio.feature.attendance.domain.model.DefaultStudentGroups
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeTransactionRunner
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.duoSpecialPackage
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.fourClassPackage
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.registrationDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.student
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RegisterStudentUseCaseTest {
    private lateinit var studentRepository: FakeStudentRepository
    private lateinit var studentPackageRepository: FakeStudentPackageRepository
    private lateinit var transactionRunner: FakeTransactionRunner
    private lateinit var registerStudent: RegisterStudentUseCase

    @BeforeTest
    fun setUp() {
        studentRepository = FakeStudentRepository()
        studentPackageRepository = FakeStudentPackageRepository()
        transactionRunner = FakeTransactionRunner()

        registerStudent = RegisterStudentUseCase(
            studentRepository = studentRepository,
            studentPackageRepository = studentPackageRepository,
            transactionRunner = transactionRunner,
        )
    }

    @Test
    fun `registering a student with a package records first attendance automatically`() = runTest {
        // When
        val result = registerStudent(
            fullName = student.fullName,
            packageType = fourClassPackage,
            registeredOn = registrationDate,
            groupId = student.groupId,
        )

        // Then
        assertEquals(student.fullName, result.student.fullName)
        assertEquals(
            result.student.id,
            result.studentPackage.studentId,
        )
        assertEquals(fourClassPackage, result.studentPackage.packageType)
        assertEquals(registrationDate, result.studentPackage.startedOn)
        assertEquals(
            setOf(registrationDate),
            result.studentPackage.attendanceDates,
        )

        // And: both student and package are persisted
        assertEquals(
            result.student,
            studentRepository.students.single(),
        )
        assertEquals(
            result.studentPackage,
            studentPackageRepository.getLatestPackageForStudent(
                result.student.id,
            ),
        )

        // And: registration runs within one transaction
        assertEquals(1, transactionRunner.runCount)
    }

    @Test
    fun `registering duo package records two visits on registration date`() = runTest {
        // When
        val result = registerStudent(
            fullName = student.fullName,
            packageType = duoSpecialPackage,
            registeredOn = registrationDate,
            visitCount = 2,
            groupId = student.groupId,
        )

        // Then
        assertEquals(
            mapOf(registrationDate to 2),
            result.studentPackage.attendanceHistory.visitsByDate,
        )
        assertEquals(2, result.studentPackage.usedClasses)
        assertEquals(6, result.studentPackage.remainingClasses)

        // And: both student and package are persisted
        assertEquals(
            result.student,
            studentRepository.students.single(),
        )
        assertEquals(
            result.studentPackage,
            studentPackageRepository.getLatestPackageForStudent(
                result.student.id,
            ),
        )
    }

    @Test
    fun `registration with invalid visit count creates neither student nor package`() = runTest {
        // When / Then
        assertFailsWith<IllegalArgumentException> {
            registerStudent(
                fullName = student.fullName,
                packageType = fourClassPackage,
                registeredOn = registrationDate,
                visitCount = 2,
                groupId = student.groupId,
            )
        }

        assertTrue(studentRepository.students.isEmpty())
        assertTrue(studentPackageRepository.packages.isEmpty())
    }

    @Test
    fun `registering a student preserves selected group`() = runTest {
        // Given
        val selectedGroupId = DefaultStudentGroups.GROUP_B.id

        // When
        val result = registerStudent(
            fullName = student.fullName,
            packageType = fourClassPackage,
            registeredOn = registrationDate,
            groupId = selectedGroupId,
        )

        // Then
        assertEquals(selectedGroupId, result.student.groupId)
        assertEquals(
            selectedGroupId,
            studentRepository.students.single().groupId,
        )
    }
}
