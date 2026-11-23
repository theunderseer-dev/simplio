package com.theunderseer.simplio.feature.attendance.presentation

import com.theunderseer.simplio.feature.attendance.domain.model.ClassAttendanceFailure
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent
import com.theunderseer.simplio.feature.attendance.domain.repository.ClassScheduleRepository
import com.theunderseer.simplio.feature.attendance.domain.repository.PackageTypeRepository
import com.theunderseer.simplio.feature.attendance.domain.repository.StudentPackageRepository
import com.theunderseer.simplio.feature.attendance.domain.repository.StudentRepository
import com.theunderseer.simplio.feature.attendance.domain.repository.attendanceSchedulesByGroup
import com.theunderseer.simplio.feature.attendance.domain.result.MarkStudentPresentResult
import com.theunderseer.simplio.feature.attendance.domain.result.RemoveStudentAttendanceResult
import com.theunderseer.simplio.feature.attendance.domain.result.RenewStudentPackageResult
import com.theunderseer.simplio.feature.attendance.domain.schedule.isAttendanceDateSelectable
import com.theunderseer.simplio.feature.attendance.domain.transaction.TransactionRunner
import com.theunderseer.simplio.feature.attendance.domain.usecase.DeleteStudentUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.MarkStudentPresentUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.RemoveStudentAttendanceUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.RenewStudentPackageUseCase
import com.theunderseer.simplio.feature.attendance.resources.Res
import com.theunderseer.simplio.feature.attendance.resources.class_cancelled_error
import com.theunderseer.simplio.feature.attendance.resources.student_delete_failed
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.getString
import kotlin.time.Clock

internal sealed interface AttendanceRosterEdit {
    val student: RegisteredStudent
    val date: LocalDate

    data class Mark(override val student: RegisteredStudent, override val date: LocalDate, val visits: Int) :
        AttendanceRosterEdit

    data class Remove(override val student: RegisteredStudent, override val date: LocalDate) : AttendanceRosterEdit

    data class Renew(
        override val student: RegisteredStudent,
        override val date: LocalDate,
        val template: PackageType,
        val visits: Int,
        val recordFirstAttendance: Boolean,
        val note: String,
    ) : AttendanceRosterEdit
}

internal class AttendanceRosterEdits(
    private val transactions: TransactionRunner,
    private val packages: StudentPackageRepository,
    private val templates: PackageTypeRepository,
    private val schedules: ClassScheduleRepository,
    students: StudentRepository,
    private val clock: Clock = Clock.System,
) {
    private val mark = MarkStudentPresentUseCase(packages, transactions)
    private val remove = RemoveStudentAttendanceUseCase(packages, transactions)
    private val renew = RenewStudentPackageUseCase(packages, students, transactions)
    private val deleteStudent = DeleteStudentUseCase(students)

    suspend fun delete(studentId: Int): String? = try {
        deleteStudent(studentId)
        null
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        getString(Res.string.student_delete_failed)
    }

    /** Returns a message for a rejected edit. Successful writes are published by Room. */
    suspend fun apply(edit: AttendanceRosterEdit): String? = try {
        transactions.run { applyInTransaction(edit) }
    } catch (error: CancellationException) {
        throw error
    } catch (_: ClassAttendanceFailure) {
        getString(Res.string.class_cancelled_error)
    } catch (_: Exception) {
        "Couldn't save the change. Try again."
    }

    private suspend fun applyInTransaction(edit: AttendanceRosterEdit): String? {
        val studentId = edit.student.student.id
        val reviewedPackageId = edit.student.studentPackage.id
        val latest = packages.getLatestPackageForStudent(studentId) ?: return PACKAGE_CHANGED_MESSAGE
        if (latest.id != reviewedPackageId) return PACKAGE_CHANGED_MESSAGE
        if (edit is AttendanceRosterEdit.Remove) {
            return remove(studentId, edit.date).errorMessage(studentId)
        }
        val groupId = edit.student.student.groupId
        val byGroup = schedules.attendanceSchedulesByGroup(listOf(groupId))
        val today = attendanceDatesForGroups(listOf(groupId), byGroup, clock.now()).getValue(groupId)
        return when (edit) {
            is AttendanceRosterEdit.Mark -> if (isAttendanceDateSelectable(edit.date, today, byGroup[groupId])) {
                mark(studentId, edit.date, edit.visits).errorMessage(studentId)
            } else {
                "Choose a scheduled class date for this group"
            }
            is AttendanceRosterEdit.Renew -> {
                if (templates.getById(edit.template.id) != edit.template) {
                    return "This package template changed. Refresh the list and choose it again."
                }
                if (!isPackagePurchaseDateSelectable(edit.date, today, latest.earliestRenewalDate, byGroup[groupId])) {
                    return "Choose an available purchase date"
                }
                renew(
                    studentId = studentId,
                    previousPackageId = reviewedPackageId,
                    packageType = edit.template,
                    renewedOn = edit.date,
                    note = edit.note,
                    visitCount = edit.visits,
                    recordFirstAttendance = edit.recordFirstAttendance,
                ).errorMessage()
            }
            is AttendanceRosterEdit.Remove -> error("Removing attendance is handled before date validation")
        }
    }
}

private const val PACKAGE_CHANGED_MESSAGE = "Package changed. Close this dialog and review it again."
private const val ALREADY_RECORDED_MESSAGE = "Attendance already recorded for this date"
private const val INVALID_VISIT_COUNT_MESSAGE = "Visit count is not allowed for this package"

internal fun MarkStudentPresentResult.errorMessage(studentId: Int): String? = when (this) {
    is MarkStudentPresentResult.Success -> null
    is MarkStudentPresentResult.PackageCompleted -> "Student package is completed"
    is MarkStudentPresentResult.PackageExpired -> "Student package has expired"
    MarkStudentPresentResult.PackageNotFound -> "Student package for student $studentId not found"
    MarkStudentPresentResult.InvalidVisitCount -> INVALID_VISIT_COUNT_MESSAGE
    MarkStudentPresentResult.InsufficientRemainingVisits -> "Not enough visits remaining"
    MarkStudentPresentResult.AttendanceAlreadyRecorded -> ALREADY_RECORDED_MESSAGE
    is MarkStudentPresentResult.AttendanceDateTooEarly ->
        "Attendance cannot be before ${earliestAllowedDate.toDisplayDate()}"
}

internal fun RenewStudentPackageResult.errorMessage(): String? = when (this) {
    is RenewStudentPackageResult.Success -> null
    is RenewStudentPackageResult.ActivePackageExists -> "Student package is still active"
    RenewStudentPackageResult.PackageNotFound -> "Student package not found"
    RenewStudentPackageResult.StudentNotFound -> "Student not found"
    RenewStudentPackageResult.PackageChanged -> PACKAGE_CHANGED_MESSAGE
    RenewStudentPackageResult.InvalidVisitCount -> INVALID_VISIT_COUNT_MESSAGE
    RenewStudentPackageResult.AttendanceAlreadyRecorded -> ALREADY_RECORDED_MESSAGE
    is RenewStudentPackageResult.RenewalDateTooEarly ->
        "Purchase cannot be before ${earliestAllowedDate.toDisplayDate()}"
}

internal fun RemoveStudentAttendanceResult.errorMessage(studentId: Int): String? = when (this) {
    is RemoveStudentAttendanceResult.Success -> null
    RemoveStudentAttendanceResult.PackageNotFound -> "Student package for student $studentId not found"
}
