package com.theunderseer.simplio.feature.attendance.data.local.repository

import com.theunderseer.simplio.feature.attendance.data.local.dao.PackageTypeDao
import com.theunderseer.simplio.feature.attendance.data.local.mapper.toDomain
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.repository.PackageTypeRepository

class RoomPackageTypeRepository(
    private val packageTypeDao: PackageTypeDao,
) : PackageTypeRepository {
    override suspend fun getAll(): List<PackageType> =
        packageTypeDao
            .getAll()
            .map { it.toDomain() }

    override suspend fun getById(id: Int): PackageType? =
        packageTypeDao
            .getById(id)
            ?.toDomain()
}
