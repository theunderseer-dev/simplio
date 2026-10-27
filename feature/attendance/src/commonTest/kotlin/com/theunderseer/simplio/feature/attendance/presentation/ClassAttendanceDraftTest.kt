package com.theunderseer.simplio.feature.attendance.presentation

import com.theunderseer.simplio.feature.attendance.fixtures.ClassAttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.testClassAttendanceSheet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClassAttendanceDraftTest {
    @Test
    fun `independent registration preserves existing unsaved selection and imports saved first visit`() {
        val sheet = testClassAttendanceSheet()
        val first = sheet.rows.single()
        val draft = ClassAttendanceUiState.Content(sheet).withVisit(first.student.id, 1)
        val added = first.copy(
            student = first.student.copy(id = first.student.id + 1, fullName = ClassAttendanceTestData.companionName),
            recordedVisitCount = 1,
        )
        val merged = draft.afterStudentSave(sheet.copy(rows = sheet.rows + added))
        assertEquals(mapOf(first.student.id to 1, added.student.id to 1), merged.visitsByStudent)
        assertTrue(merged.hasDraftChanges)
        assertTrue(merged.hasIndependentChanges)
    }

    @Test
    fun `independent renewal preserves unchecked saved visits and untouched rows use new persisted state`() {
        val sheet = testClassAttendanceSheet(recordedVisitCount = 1)
        val row = sheet.rows.single()
        val unchecked = ClassAttendanceUiState.Content(sheet).withVisit(row.student.id, 0)
        val merged = unchecked.afterStudentSave(sheet)
        assertEquals(emptyMap(), merged.visitsByStudent)
        assertTrue(merged.hasDraftChanges)
        val unchanged = ClassAttendanceUiState.Content(sheet).afterStudentSave(sheet)
        assertFalse(unchanged.hasDraftChanges)
    }
}
