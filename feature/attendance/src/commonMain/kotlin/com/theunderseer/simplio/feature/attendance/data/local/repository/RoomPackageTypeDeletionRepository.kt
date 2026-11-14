package com.theunderseer.simplio.feature.attendance.data.local.repository

import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomSyncJournal
import com.theunderseer.simplio.feature.attendance.domain.repository.PackageTypeDeletionRepository

class RoomPackageTypeDeletionRepository(private val database: AttendanceDatabase) : PackageTypeDeletionRepository {
    override suspend fun delete(packageTypeId: Int) = RoomSyncJournal.write(database) { journal ->
        if (database.packageTypeDao().archive(packageTypeId) != 0) {
            journal.archivePackageType(packageTypeId)
        }
    }
}
