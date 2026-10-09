package com.theunderseer.simplio.feature.attendance.data.local.mapper

import com.theunderseer.simplio.feature.attendance.data.local.entity.PackageTypeEntity
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import kotlin.test.Test
import kotlin.test.assertEquals

class PackageTypeMapperTest {
    @Test
    fun `maps individual package with one visit per attendance`() {
        // Given
        val entity =
            PackageTypeEntity(
                id = 1,
                name = "4-class package",
                totalClasses = 4,
                validityWeeks = 4,
                price = 1400,
            )

        // When
        val result = entity.toDomain()

        // Then
        assertEquals(
            PackageType(
                id = 1,
                name = "4-class package",
                totalClasses = 4,
                validityWeeks = 4,
                price = 1400,
                maxVisitsPerAttendance = 1,
            ),
            result,
        )
    }

    @Test
    fun `maps paired package with two visits per attendance`() {
        // Given
        val entity =
            PackageTypeEntity(
                id = 2,
                name = "Duo 8 · Special rate",
                totalClasses = 8,
                validityWeeks = 4,
                price = 2400,
                maxVisitsPerAttendance = 2,
            )

        // When
        val result = entity.toDomain()

        // Then
        assertEquals(
            PackageType(
                id = 2,
                name = "Duo 8 · Special rate",
                totalClasses = 8,
                validityWeeks = 4,
                price = 2400,
                maxVisitsPerAttendance = 2,
            ),
            result,
        )
    }
}
