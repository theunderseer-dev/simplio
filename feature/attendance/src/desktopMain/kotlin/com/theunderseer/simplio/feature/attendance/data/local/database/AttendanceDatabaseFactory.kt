package com.theunderseer.simplio.feature.attendance.data.local.database

import androidx.room.Room

fun createAttendanceDatabase(path: String): AttendanceDatabase = Room
    .databaseBuilder<AttendanceDatabase>(
        name = path,
    ).configureAttendanceDatabase()
    .build()
