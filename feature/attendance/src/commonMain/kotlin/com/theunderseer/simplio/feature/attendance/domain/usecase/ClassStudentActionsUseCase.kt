package com.theunderseer.simplio.feature.attendance.domain.usecase

import com.theunderseer.simplio.feature.attendance.domain.model.ClassAttendanceFailure
import com.theunderseer.simplio.feature.attendance.domain.model.ClassAttendanceProblem
import com.theunderseer.simplio.feature.attendance.domain.model.ClassAttendanceSheet
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.repository.ClassAttendanceRepository
import com.theunderseer.simplio.feature.attendance.domain.result.RenewStudentPackageResult
import com.theunderseer.simplio.feature.attendance.domain.transaction.TransactionRunner

class ClassStudentActionsUseCase(
    private val classRepository: ClassAttendanceRepository,
    private val registerStudent: RegisterStudentUseCase,
    private val renewPackage: RenewStudentPackageUseCase,
    private val transactionRunner: TransactionRunner,
) {
    suspend fun register(
        expected: ClassAttendanceSheet,
        fullName: String,
        groupId: Int,
        packageType: PackageType,
        visitCount: Int,
        recordFirstAttendance: Boolean,
        note: String,
    ): ClassAttendanceSheet = transactionRunner.run {
        checkCurrent(expected)
        if (groupId !in expected.groupIds) throw ClassAttendanceFailure(ClassAttendanceProblem.INVALID_SELECTION)
        registerStudent(fullName, groupId, packageType, expected.session.date, visitCount, recordFirstAttendance, note)
        classRepository.load(expected.session.id)
    }

    suspend fun renew(
        expected: ClassAttendanceSheet,
        studentId: Int,
        packageType: PackageType,
        visitCount: Int,
        recordFirstAttendance: Boolean,
        note: String,
    ): ClassAttendanceSheet = transactionRunner.run {
        checkCurrent(expected)
        val row = expected.rows.singleOrNull { it.student.id == studentId }
            ?: throw ClassAttendanceFailure(ClassAttendanceProblem.INVALID_SELECTION)
        if (!row.canRenew) throw ClassAttendanceFailure(ClassAttendanceProblem.INVALID_SELECTION)
        val result = renewPackage(
            studentId,
            checkNotNull(row.studentPackage).id,
            packageType,
            expected.session.date,
            note,
            visitCount,
            recordFirstAttendance,
        )
        if (result !is RenewStudentPackageResult.Success) {
            throw ClassAttendanceFailure(ClassAttendanceProblem.INVALID_SELECTION)
        }
        classRepository.load(expected.session.id)
    }

    private suspend fun checkCurrent(expected: ClassAttendanceSheet) {
        if (classRepository.load(expected.session.id) != expected) {
            throw ClassAttendanceFailure(ClassAttendanceProblem.STALE)
        }
    }
}
