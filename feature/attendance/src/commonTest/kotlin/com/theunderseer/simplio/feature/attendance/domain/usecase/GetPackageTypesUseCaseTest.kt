package com.theunderseer.simplio.feature.attendance.domain.usecase

import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.repository.PackageTypeRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GetPackageTypesUseCaseTest {
    @Test
    fun `returns available package types`() = runTest {
        val packageTypes =
            listOf(
                PackageType(
                    id = 1,
                    name = "4-class package",
                    totalClasses = 4,
                    validityWeeks = 5,
                    price = 1400,
                ),
            )

        val repository =
            object : PackageTypeRepository {
                override suspend fun getAll(): List<PackageType> = packageTypes

                override suspend fun getById(id: Int): PackageType? = packageTypes.find { it.id == id }
            }

        val useCase =
            GetPackageTypesUseCase(
                packageTypeRepository = repository,
            )

        assertEquals(
            packageTypes,
            useCase(),
        )
    }
}
