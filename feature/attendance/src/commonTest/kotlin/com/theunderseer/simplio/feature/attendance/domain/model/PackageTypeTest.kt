package com.theunderseer.simplio.feature.attendance.domain.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PackageTypeTest {
    @Test
    fun `individual package allows exactly one visit per attendance date`() {
        // Given
        val packageType =
            PackageType(
                id = 1,
                name = "4-class package",
                totalClasses = 4,
                validityWeeks = 4,
                price = 1400,
            )

        // Then
        assertTrue(packageType.allowsVisitCount(1))
        assertFalse(packageType.allowsVisitCount(2))
        assertFalse(packageType.allowsVisitCount(0))
        assertFalse(packageType.allowsVisitCount(-1))
    }

    @Test
    fun `paired package allows one or two visits per attendance date`() {
        // Given
        val packageType =
            PackageType(
                id = 3,
                name = "Duo 8 · Special rate",
                totalClasses = 8,
                validityWeeks = 4,
                price = 2400,
                maxVisitsPerAttendance = 2,
            )

        // Then
        assertTrue(packageType.allowsVisitCount(1))
        assertTrue(packageType.allowsVisitCount(2))
        assertFalse(packageType.allowsVisitCount(3))
        assertFalse(packageType.allowsVisitCount(0))
        assertFalse(packageType.allowsVisitCount(-1))
    }
}
