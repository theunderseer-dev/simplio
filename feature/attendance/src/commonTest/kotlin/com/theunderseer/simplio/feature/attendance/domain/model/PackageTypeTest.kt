package com.theunderseer.simplio.feature.attendance.domain.model

import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.duoSpecialPackage
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.fiveClassPackage
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.fourClassPackage
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PackageTypeTest {
    @Test
    fun `individual package allows exactly one visit per attendance date`() {
        // Given
        val individualPackages = listOf(
            fourClassPackage,
            fiveClassPackage,
        )

        // Then
        individualPackages.forEach { packageType ->
            assertTrue(packageType.allowsVisitCount(1))
            assertFalse(packageType.allowsVisitCount(2))
            assertFalse(packageType.allowsVisitCount(0))
            assertFalse(packageType.allowsVisitCount(-1))
        }
    }

    @Test
    fun `duo package allows one or two visits per attendance date`() {
        // Given
        val packageType = duoSpecialPackage

        // Then
        assertTrue(packageType.allowsVisitCount(1))
        assertTrue(packageType.allowsVisitCount(2))
        assertFalse(packageType.allowsVisitCount(3))
        assertFalse(packageType.allowsVisitCount(0))
        assertFalse(packageType.allowsVisitCount(-1))
    }
}
