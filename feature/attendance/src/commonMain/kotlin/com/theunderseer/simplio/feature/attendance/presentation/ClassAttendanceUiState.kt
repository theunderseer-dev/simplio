package com.theunderseer.simplio.feature.attendance.presentation

import com.theunderseer.simplio.feature.attendance.domain.model.ClassAttendanceProblem
import com.theunderseer.simplio.feature.attendance.domain.model.ClassAttendanceSheet

sealed interface ClassAttendanceUiState {
    data object Closed : ClassAttendanceUiState
    data object Loading : ClassAttendanceUiState
    data class LoadError(val sessionId: String) : ClassAttendanceUiState
    data class Content(
        val sheet: ClassAttendanceSheet,
        val isSaving: Boolean = false,
        val problem: ClassAttendanceProblem? = null,
        val saveFailed: Boolean = false,
        val visitsByStudent: Map<Int, Int> = sheet.recordedVisits(),
        val studentAction: ClassStudentAction? = null,
        val hasIndependentChanges: Boolean = false,
    ) : ClassAttendanceUiState {
        val hasDraftChanges: Boolean get() = visitsByStudent != sheet.recordedVisits()

        fun withVisit(studentId: Int, count: Int): Content {
            if (isSaving || studentAction != null) return this
            val row = sheet.rows.singleOrNull { it.student.id == studentId } ?: return this
            if (!row.canRecord || count !in 0..row.maximumVisitCount) return this
            val updated = if (count == 0) visitsByStudent - studentId else visitsByStudent + (studentId to count)
            return copy(visitsByStudent = updated)
        }

        fun afterStudentSave(updated: ClassAttendanceSheet): Content {
            val dirtyIds = sheet.rows.filter {
                (visitsByStudent[it.student.id] ?: 0) != it.recordedVisitCount
            }.map { it.student.id }.toSet()
            val merged = updated.recordedVisits().toMutableMap()
            dirtyIds.forEach { id ->
                val count = visitsByStudent[id] ?: 0
                if (count == 0) merged.remove(id) else merged[id] = count
            }
            return copy(
                sheet = updated,
                visitsByStudent = merged.toMap(),
                studentAction = null,
                hasIndependentChanges = true,
                problem = null,
                saveFailed = false,
            )
        }
    }
}

private fun ClassAttendanceSheet.recordedVisits(): Map<Int, Int> = rows.filter { it.recordedVisitCount > 0 }
    .associate { it.student.id to it.recordedVisitCount }

enum class ClassStudentActionKind { REGISTER, RENEW }

data class ClassStudentAction(
    val kind: ClassStudentActionKind,
    val studentId: Int?,
    val isSaving: Boolean = false,
    val problem: ClassAttendanceProblem? = null,
    val saveFailed: Boolean = false,
    val creatingPackage: Boolean = false,
    val preferredPackageTypeId: Int? = null,
)
