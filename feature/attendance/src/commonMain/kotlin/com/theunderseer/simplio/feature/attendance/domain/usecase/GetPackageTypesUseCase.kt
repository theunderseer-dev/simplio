package com.theunderseer.simplio.feature.attendance.domain.usecase

import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.repository.PackageTypeRepository

class GetPackageTypesUseCase(private val packageTypeRepository: PackageTypeRepository) {
    suspend operator fun invoke(): List<PackageType> = packageTypeRepository.getAll()
}
