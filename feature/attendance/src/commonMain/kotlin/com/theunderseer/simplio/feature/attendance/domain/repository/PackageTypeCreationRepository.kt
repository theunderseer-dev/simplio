package com.theunderseer.simplio.feature.attendance.domain.repository

import com.theunderseer.simplio.feature.attendance.domain.model.CreatePackageTypeRequest
import com.theunderseer.simplio.feature.attendance.domain.model.CreatePackageTypeResult

interface PackageTypeCreationRepository {
    suspend fun create(request: CreatePackageTypeRequest): CreatePackageTypeResult
}
