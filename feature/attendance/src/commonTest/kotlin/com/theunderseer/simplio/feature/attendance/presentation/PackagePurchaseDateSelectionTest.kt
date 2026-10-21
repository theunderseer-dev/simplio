package com.theunderseer.simplio.feature.attendance.presentation

import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.currentDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.firstClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.secondClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.thirdClassDate
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PackagePurchaseDateSelectionTest {
    @Test
    fun `renewal keeps both date bounds in addition to the schedule`() {
        assertFalse(isPackagePurchaseDateSelectable(firstClassDate, thirdClassDate, secondClassDate))
        assertTrue(isPackagePurchaseDateSelectable(secondClassDate, thirdClassDate, secondClassDate))
        assertTrue(isPackagePurchaseDateSelectable(thirdClassDate, thirdClassDate, secondClassDate))
        assertFalse(isPackagePurchaseDateSelectable(thirdClassDate, currentDate, secondClassDate))
        assertFalse(isPackagePurchaseDateSelectable(currentDate, thirdClassDate, secondClassDate))
    }

    @Test
    fun `initial date stays on a scheduled today`() {
        assertEquals(firstClassDate, initialPackagePurchaseDate(firstClassDate, null))
        assertEquals(secondClassDate, initialPackagePurchaseDate(secondClassDate, firstClassDate))
    }

    @Test
    fun `initial date moves back to the latest scheduled day`() {
        for (day in listOf(3, 4)) {
            assertEquals(firstClassDate, initialPackagePurchaseDate(LocalDate(2026, 10, day), null))
        }
        for (day in listOf(6, 7, 8)) {
            assertEquals(secondClassDate, initialPackagePurchaseDate(LocalDate(2026, 10, day), null))
        }
    }

    @Test
    fun `initial date is empty when the next eligible class is still in the future`() {
        assertNull(initialPackagePurchaseDate(currentDate, LocalDate(2026, 10, 6)))
        assertNull(initialPackagePurchaseDate(firstClassDate, secondClassDate))
    }

    @Test
    fun `a non scheduled lower bound does not enable that date or skip the next valid class`() {
        val earliest = LocalDate(2026, 10, 3)
        assertFalse(isPackagePurchaseDateSelectable(earliest, secondClassDate, earliest))
        assertEquals(secondClassDate, initialPackagePurchaseDate(secondClassDate, earliest))
    }
}
