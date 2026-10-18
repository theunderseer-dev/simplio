package com.theunderseer.simplio.feature.attendance.data.local.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

fun createAttendanceDatabase(path: String): AttendanceDatabase = Room
    .databaseBuilder<AttendanceDatabase>(
        name = path,
    ).setDriver(BundledSQLiteDriver())
    .addMigrations(
        AttendanceMigration1To2,
        AttendanceMigration2To3,
        AttendanceMigration3To4,
        AttendanceMigration4To5,
        AttendanceMigration5To6,
    )
    .build()
