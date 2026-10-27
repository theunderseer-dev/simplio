package com.theunderseer.simplio.feature.attendance.data.local.sync

import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.entity.SyncOutboxEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.SyncRecordEntity
import com.theunderseer.simplio.feature.attendance.data.local.seed.attachDefaultSchedule
import com.theunderseer.simplio.feature.attendance.data.local.transaction.RoomTransactionRunner
import com.theunderseer.simplio.feature.attendance.domain.model.PackagePlan
import kotlin.uuid.Uuid

/** Only created inside the transaction that also changes the business rows. */
internal class RoomSyncJournal private constructor(private val database: AttendanceDatabase) {
    private val dao = database.syncDao()

    suspend fun ensureClassSession(id: String): SyncRecordEntity {
        val state = checkNotNull(database.classSessionStateDao().get(id))
        ensureSchedule(state.scheduleId)
        return ensure(SyncEntityType.CLASS_SESSION, id, "CLASS_SESSION:$id", id)
    }

    suspend fun ensureSchedule(id: String): SyncRecordEntity {
        check(database.classScheduleDao().schedules().any { it.id == id })
        return ensure(SyncEntityType.CLASS_SCHEDULE, id, "CLASS_SCHEDULE:$id", id)
    }

    suspend fun upsertSchedule(id: String) {
        val existing = dao.getRecord(SyncEntityType.CLASS_SCHEDULE, id)
        if (existing == null) ensureSchedule(id) else change(existing, deleted = false)
    }

    suspend fun upsertGroup(id: Int) {
        val existing = dao.getRecord(SyncEntityType.STUDENT_GROUP, id.toString())
        if (existing == null) ensureGroup(id) else change(existing, deleted = false)
    }

    suspend fun ensureGroup(id: Int): SyncRecordEntity {
        database.attachDefaultSchedule(id)
        dao.getRecord(SyncEntityType.STUDENT_GROUP, id.toString())?.let { return it }
        val group = checkNotNull(database.studentGroupDao().getAll().find { it.id == id })
        val cloudId = when {
            id == 1 && group.name == "Group A" -> "default-group-a"
            id == 2 && group.name == "Group B" -> "default-group-b"
            else -> null
        }
        return ensure(SyncEntityType.STUDENT_GROUP, id.toString(), "STUDENT_GROUP:$id", cloudId)
    }

    suspend fun ensurePackageType(id: Int): SyncRecordEntity {
        dao.getRecord(SyncEntityType.PACKAGE_TYPE, id.toString())?.let { return it }
        val types = database.packageTypeDao().getAll()
        val type = checkNotNull(types.find { it.id == id })
        val plan = PackagePlan.entries.find {
            it.packageName == type.name &&
                it.totalClasses == type.totalClasses &&
                it.validityWeeks == type.validityWeeks &&
                it.price == type.price &&
                it.maxVisitsPerAttendance == type.maxVisitsPerAttendance
        }
        val isFirst = types.firstOrNull {
            it.name == type.name &&
                it.totalClasses == type.totalClasses &&
                it.validityWeeks == type.validityWeeks &&
                it.price == type.price &&
                it.maxVisitsPerAttendance == type.maxVisitsPerAttendance
        }?.id == id
        val cloudId = if (plan != null && isFirst) "default-plan-${plan.name.lowercase()}" else null
        return ensure(SyncEntityType.PACKAGE_TYPE, id.toString(), "PACKAGE_TYPE:$id", cloudId)
    }

    suspend fun ensureStudent(id: Int): SyncRecordEntity {
        dao.getRecord(SyncEntityType.STUDENT, id.toString())?.let { return it }
        val student = checkNotNull(database.studentDao().getById(id))
        ensureGroup(student.groupId)
        return ensure(SyncEntityType.STUDENT, id.toString(), "STUDENT:$id")
    }

    suspend fun ensurePackage(id: Int): SyncRecordEntity {
        dao.getRecord(SyncEntityType.STUDENT_PACKAGE, id.toString())?.let { return it }
        val studentPackage = checkNotNull(database.studentPackageDao().getById(id))
        ensureStudent(studentPackage.studentId)
        ensurePackageType(studentPackage.packageTypeId)
        return ensure(SyncEntityType.STUDENT_PACKAGE, id.toString(), "STUDENT:${studentPackage.studentId}")
    }

    suspend fun ensureAttendance(packageId: Int, epochDay: Long): SyncRecordEntity {
        dao.getRecord(SyncEntityType.ATTENDANCE, attendanceLocalId(packageId, epochDay))?.let { return it }
        val parent = ensurePackage(packageId)
        return ensure(
            SyncEntityType.ATTENDANCE,
            attendanceLocalId(packageId, epochDay),
            parent.aggregateKey,
            "${parent.cloudId}_$epochDay",
        )
    }

    suspend fun upsertStudent(id: Int) {
        val existing = dao.getRecord(SyncEntityType.STUDENT, id.toString())
        if (existing == null) ensureStudent(id) else change(existing, deleted = false)
    }

    suspend fun upsertAttendance(packageId: Int, epochDay: Long) {
        val existing = dao.getRecord(SyncEntityType.ATTENDANCE, attendanceLocalId(packageId, epochDay))
        if (existing == null) ensureAttendance(packageId, epochDay) else change(existing, deleted = false)
    }

    suspend fun deleteAttendance(packageId: Int, epochDay: Long) =
        change(ensureAttendance(packageId, epochDay), deleted = true)

    suspend fun deleteStudent(id: Int) {
        val student = ensureStudent(id)
        database.studentPackageDao().getForStudent(id).forEach { studentPackage ->
            val parent = ensurePackage(studentPackage.id)
            database.attendanceDao().getForPackage(studentPackage.id).forEach { attendance ->
                deleteAttendance(studentPackage.id, attendance.attendedOnEpochDay)
            }
            change(parent, deleted = true)
        }
        change(student, deleted = true)
    }

    private suspend fun ensure(
        type: SyncEntityType,
        localId: String,
        aggregateKey: String,
        cloudId: String? = null,
    ): SyncRecordEntity {
        dao.getRecord(type, localId)?.let { return it }
        val record = SyncRecordEntity(type, localId, cloudId ?: newId(), revision = 1, aggregateKey = aggregateKey)
        dao.saveRecord(record)
        enqueue(record)
        return record
    }

    private suspend fun change(record: SyncRecordEntity, deleted: Boolean) {
        val updated = record.copy(revision = record.revision + 1, isDeleted = deleted)
        dao.saveRecord(updated)
        enqueue(updated)
    }

    private suspend fun enqueue(record: SyncRecordEntity) {
        dao.enqueue(
            SyncOutboxEntity(
                entityType = record.entityType,
                localId = record.localId,
                revision = record.revision,
                operation = if (record.isDeleted) SyncOperation.DELETE else SyncOperation.UPSERT,
                mutationId = newId(),
            ),
        )
    }

    companion object {
        suspend fun <T> write(database: AttendanceDatabase, block: suspend (RoomSyncJournal) -> T): T =
            RoomTransactionRunner(database).run { block(RoomSyncJournal(database)) }

        private fun newId(): String = Uuid.random().toHexString()

        private fun attendanceLocalId(packageId: Int, epochDay: Long): String = "$packageId:$epochDay"
    }
}
