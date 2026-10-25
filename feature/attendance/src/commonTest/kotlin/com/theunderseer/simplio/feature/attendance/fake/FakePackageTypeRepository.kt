package com.theunderseer.simplio.feature.attendance.fake

import com.theunderseer.simplio.feature.attendance.domain.model.CreatePackageTypeRequest
import com.theunderseer.simplio.feature.attendance.domain.model.CreatePackageTypeResult
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.displayPackageName
import com.theunderseer.simplio.feature.attendance.domain.model.normalizedPackageName
import com.theunderseer.simplio.feature.attendance.domain.model.packageTypeValidation
import com.theunderseer.simplio.feature.attendance.domain.repository.PackageTypeCreationRepository
import com.theunderseer.simplio.feature.attendance.domain.repository.PackageTypeRepository

internal class FakePackageTypeRepository(initial: List<PackageType>) :
    PackageTypeRepository,
    PackageTypeCreationRepository {
    private val types = initial.toMutableList()
    override suspend fun getAll(): List<PackageType> = types.toList()
    override suspend fun getById(id: Int): PackageType? = types.find { it.id == id }
    override suspend fun create(request: CreatePackageTypeRequest): CreatePackageTypeResult {
        packageTypeValidation(request)?.let { return CreatePackageTypeResult.Invalid(it) }
        if (types.any { normalizedPackageName(it.name) == normalizedPackageName(request.name) }) {
            return CreatePackageTypeResult.DuplicateName
        }
        val type = PackageType(
            (types.maxOfOrNull { it.id } ?: 0) + 1,
            displayPackageName(request.name),
            request.totalClasses,
            request.validityWeeks,
            request.price,
            request.maxVisitsPerAttendance,
        )
        types += type
        return CreatePackageTypeResult.Success(type)
    }
}
