package com.theunderseer.simplio.feature.attendance.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.theunderseer.simplio.feature.attendance.data.local.dao.AttendanceDao
import com.theunderseer.simplio.feature.attendance.data.local.dao.PackageTypeDao
import com.theunderseer.simplio.feature.attendance.data.local.dao.StudentDao
import com.theunderseer.simplio.feature.attendance.data.local.dao.StudentGroupDao
import com.theunderseer.simplio.feature.attendance.data.local.dao.StudentPackageDao
import com.theunderseer.simplio.feature.attendance.data.local.entity.AttendanceEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.PackageTypeEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentGroupEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentPackageEntity

@Database(
    entities = [
        StudentGroupEntity::class,
        StudentEntity::class,
        PackageTypeEntity::class,
        StudentPackageEntity::class,
        AttendanceEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class AttendanceDatabase : RoomDatabase() {
    abstract fun studentGroupDao(): StudentGroupDao

    abstract fun studentDao(): StudentDao

    abstract fun packageTypeDao(): PackageTypeDao

    abstract fun studentPackageDao(): StudentPackageDao

    abstract fun attendanceDao(): AttendanceDao
}
