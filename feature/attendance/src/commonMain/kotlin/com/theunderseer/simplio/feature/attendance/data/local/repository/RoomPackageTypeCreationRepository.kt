package com.theunderseer.simplio.feature.attendance.data.local.repository

import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.entity.PackageTypeEntity
import com.theunderseer.simplio.feature.attendance.data.local.mapper.toDomain
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomSyncJournal
import com.theunderseer.simplio.feature.attendance.domain.model.CreatePackageTypeRequest
import com.theunderseer.simplio.feature.attendance.domain.model.CreatePackageTypeResult
import com.theunderseer.simplio.feature.attendance.domain.model.displayPackageName
import com.theunderseer.simplio.feature.attendance.domain.model.normalizedPackageName
import com.theunderseer.simplio.feature.attendance.domain.model.packageTypeValidation
import com.theunderseer.simplio.feature.attendance.domain.repository.PackageTypeCreationRepository

class RoomPackageTypeCreationRepository(private val database: AttendanceDatabase) : PackageTypeCreationRepository {
    override suspend fun create(request: CreatePackageTypeRequest): CreatePackageTypeResult =
        RoomSyncJournal.write(database) { journal ->
            packageTypeValidation(request)?.let { return@write CreatePackageTypeResult.Invalid(it) }
            val dao = database.packageTypeDao()
            if (dao.getAll().any { normalizedPackageName(it.name) == normalizedPackageName(request.name) }) {
                return@write CreatePackageTypeResult.DuplicateName
            }
            val entity = PackageTypeEntity(
                name = displayPackageName(request.name),
                totalClasses = request.totalClasses,
                validityWeeks = request.validityWeeks,
                price = request.price,
                maxVisitsPerAttendance = request.maxVisitsPerAttendance,
            )
            val id = dao.insert(entity).toInt()
            journal.ensurePackageType(id)
            CreatePackageTypeResult.Success(entity.copy(id = id).toDomain())
        }
}
