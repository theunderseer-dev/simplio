package com.theunderseer.simplio.feature.attendance.data.local.mapper

import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.duoSpecialPackage
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.fourClassPackage
import com.theunderseer.simplio.feature.attendance.fixtures.testPackageTypeEntity
import kotlin.test.Test
import kotlin.test.assertEquals

class PackageTypeMapperTest {
    @Test
    fun `maps individual package with one visit per attendance`() {
        // Given
        val expected = fourClassPackage.copy(
            id = 7,
            name = "Custom individual package",
            totalClasses = 7,
            validityWeeks = 6,
            price = 1900,
        )
        val entity = testPackageTypeEntity(
            packageType = expected,
        ).copy(id = expected.id)

        // When
        val result = entity.toDomain()

        // Then
        assertEquals(expected, result)
    }

    @Test
    fun `maps duo package with two visits per attendance`() {
        // Given
        val expected = duoSpecialPackage
        val entity = testPackageTypeEntity(
            packageType = expected,
        ).copy(id = expected.id)

        // When
        val result = entity.toDomain()

        // Then
        assertEquals(expected, result)
    }
}
