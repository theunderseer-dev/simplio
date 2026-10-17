package com.theunderseer.simplio.feature.attendance.presentation.viewmodel

import com.theunderseer.simplio.feature.attendance.fake.FakeStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentRepository
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.presentation.AttendanceUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceViewModelPrepaidRegistrationTest {
    @Test
    fun `registering without first attendance refreshes content with prepaid package`() = runViewModelTest {
        // Given
        val studentRepository = FakeStudentRepository()
        val studentPackageRepository = FakeStudentPackageRepository()

        val viewModel = createViewModel(
            studentRepository = studentRepository,
            studentPackageRepository = studentPackageRepository,
        )

        observeUiState(viewModel)
        advanceUntilIdle()

        // When
        viewModel.registerStudent(
            fullName = AttendanceTestData.student.fullName,
            groupId = AttendanceTestData.student.groupId,
            packageType = AttendanceTestData.fourClassPackage,
            registeredOn = AttendanceTestData.registrationDate,
            recordFirstAttendance = false,
        )
        advanceUntilIdle()

        // Then
        val state = assertIs<AttendanceUiState.Content>(
            viewModel.uiState.value,
        )
        val registeredStudent = state.attendance.entries.single()
        val studentPackage = registeredStudent.studentPackage

        assertEquals(
            AttendanceTestData.registrationDate,
            studentPackage.purchasedOn,
        )
        assertNull(studentPackage.startedOn)
        assertNull(studentPackage.expiresOn)
        assertEquals(0, studentPackage.usedClasses)
        assertEquals(4, studentPackage.remainingClasses)

        assertEquals(
            registeredStudent.student,
            studentRepository.students.single(),
        )
        assertEquals(
            studentPackage,
            studentPackageRepository.getLatestPackageForStudent(
                registeredStudent.student.id,
            ),
        )
    }
}
