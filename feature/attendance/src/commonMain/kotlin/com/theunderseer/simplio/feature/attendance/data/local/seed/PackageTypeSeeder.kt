package com.theunderseer.simplio.feature.attendance.data.local.seed

import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.entity.PackageTypeEntity
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomSyncJournal
import com.theunderseer.simplio.feature.attendance.domain.model.PackagePlan

class PackageTypeSeeder(private val database: AttendanceDatabase) {
    private val packageTypeDao = database.packageTypeDao()

    suspend fun seedDefaults() {
        RoomSyncJournal.write(database) { journal ->
            if (database.syncUploadDao().getState()?.lastPullReadTime != null) return@write
            val existingNames =
                packageTypeDao.getAll()
                    .map { it.name }
                    .toSet()

            defaultPackageTypes
                .filterNot { it.name in existingNames }
                .forEach { packageType ->
                    packageTypeDao.insert(packageType)
                }

            packageTypeDao.getAll().forEach { journal.ensurePackageType(it.id) }
        }
    }

    private val defaultPackageTypes =
        PackagePlan.entries.map { plan ->
            PackageTypeEntity(
                name = plan.packageName,
                totalClasses = plan.totalClasses,
                validityWeeks = plan.validityWeeks,
                price = plan.price,
                maxVisitsPerAttendance = plan.maxVisitsPerAttendance,
            )
        }
}
