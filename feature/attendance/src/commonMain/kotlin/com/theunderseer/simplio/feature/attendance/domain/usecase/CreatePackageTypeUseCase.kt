package com.theunderseer.simplio.feature.attendance.domain.usecase

import com.theunderseer.simplio.feature.attendance.domain.model.CreatePackageTypeRequest
import com.theunderseer.simplio.feature.attendance.domain.model.CreatePackageTypeResult
import com.theunderseer.simplio.feature.attendance.domain.model.packageTypeValidation
import com.theunderseer.simplio.feature.attendance.domain.repository.PackageTypeCreationRepository

class CreatePackageTypeUseCase(private val repository: PackageTypeCreationRepository) {
    suspend operator fun invoke(request: CreatePackageTypeRequest): CreatePackageTypeResult {
        packageTypeValidation(request)?.let { return CreatePackageTypeResult.Invalid(it) }
        return repository.create(request)
    }
}
