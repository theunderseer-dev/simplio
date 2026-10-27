package com.theunderseer.simplio.feature.attendance.data.local.seed

import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentGroupEntity
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomSyncJournal
import com.theunderseer.simplio.feature.attendance.data.local.sync.SyncEntityType
import com.theunderseer.simplio.feature.attendance.domain.model.DefaultStudentGroups

class StudentGroupSeeder(private val database: AttendanceDatabase) {
    private val studentGroupDao = database.studentGroupDao()

    suspend fun seedDefaults() {
        RoomSyncJournal.write(database) { journal ->
            if (database.syncUploadDao().getState()?.lastPullReadTime != null) return@write
            val existingIds =
                studentGroupDao.getAll()
                    .map { it.id }
                    .toSet()

            defaultStudentGroups
                .filterNot {
                    it.id in existingIds ||
                        database.syncDao().getRecord(SyncEntityType.STUDENT_GROUP, it.id.toString())?.isDeleted == true
                }
                .forEach { group ->
                    studentGroupDao.insert(group)
                }

            studentGroupDao.getAll().forEach {
                database.attachDefaultSchedule(it.id)
                journal.ensureGroup(it.id)
            }
        }
    }

    private val defaultStudentGroups =
        DefaultStudentGroups.ALL.map { group ->
            StudentGroupEntity(
                id = group.id,
                name = group.name,
            )
        }
}
