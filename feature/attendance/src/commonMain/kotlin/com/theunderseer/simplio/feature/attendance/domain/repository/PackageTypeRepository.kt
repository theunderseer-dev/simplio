package com.theunderseer.simplio.feature.attendance.domain.repository

import com.theunderseer.simplio.feature.attendance.domain.model.PackageType

interface PackageTypeRepository {
    suspend fun getAll(): List<PackageType>

    suspend fun getById(id: Int): PackageType?
}
