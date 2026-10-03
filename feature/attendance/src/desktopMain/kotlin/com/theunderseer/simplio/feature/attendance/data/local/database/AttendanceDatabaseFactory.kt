package com.theunderseer.simplio.feature.attendance.data.local.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

fun createAttendanceDatabase(path: String): AttendanceDatabase = Room
    .databaseBuilder<AttendanceDatabase>(
        name = path,
    ).setDriver(BundledSQLiteDriver())
    .build()
