package com.theunderseer.simplio.feature.attendance.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.theunderseer.simplio.feature.attendance.data.local.dao.AttendanceDao
import com.theunderseer.simplio.feature.attendance.data.local.dao.ClassScheduleDao
import com.theunderseer.simplio.feature.attendance.data.local.dao.PackageTypeDao
import com.theunderseer.simplio.feature.attendance.data.local.dao.StudentDao
import com.theunderseer.simplio.feature.attendance.data.local.dao.StudentGroupDao
import com.theunderseer.simplio.feature.attendance.data.local.dao.StudentPackageDao
import com.theunderseer.simplio.feature.attendance.data.local.dao.SyncConflictDao
import com.theunderseer.simplio.feature.attendance.data.local.dao.SyncDao
import com.theunderseer.simplio.feature.attendance.data.local.dao.SyncPullDao
import com.theunderseer.simplio.feature.attendance.data.local.dao.SyncUploadDao
import com.theunderseer.simplio.feature.attendance.data.local.entity.AttendanceEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.ClassScheduleEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.ClassScheduleSlotEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.PackageTypeEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentGroupEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentGroupScheduleEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentPackageEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.SyncConflictReviewEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.SyncOutboxEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.SyncRecordEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.SyncUploadBatchEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.SyncUploadStateEntity

@Database(
    entities = [
        ClassScheduleEntity::class,
        ClassScheduleSlotEntity::class,
        StudentGroupScheduleEntity::class,
        StudentGroupEntity::class,
        StudentEntity::class,
        PackageTypeEntity::class,
        StudentPackageEntity::class,
        AttendanceEntity::class,
        SyncRecordEntity::class,
        SyncOutboxEntity::class,
        SyncUploadStateEntity::class,
        SyncUploadBatchEntity::class,
        SyncConflictReviewEntity::class,
    ],
    version = 12,
    exportSchema = true,
)
abstract class AttendanceDatabase : RoomDatabase() {
    abstract fun classScheduleDao(): ClassScheduleDao

    abstract fun studentGroupDao(): StudentGroupDao

    abstract fun studentDao(): StudentDao

    abstract fun packageTypeDao(): PackageTypeDao

    abstract fun studentPackageDao(): StudentPackageDao

    abstract fun syncConflictDao(): SyncConflictDao

    abstract fun syncPullDao(): SyncPullDao

    abstract fun syncUploadDao(): SyncUploadDao

    abstract fun syncDao(): SyncDao

    abstract fun attendanceDao(): AttendanceDao
}
