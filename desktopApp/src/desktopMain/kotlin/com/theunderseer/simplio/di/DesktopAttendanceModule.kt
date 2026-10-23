package com.theunderseer.simplio.di

import com.theunderseer.simplio.attendance.time.attendanceDates
import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.database.createAttendanceDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.dsl.onClose
import kotlin.time.Clock

internal fun desktopAttendanceModule(databasePath: String, clock: Clock = Clock.System): Module = module {
    single<Clock>(named("attendanceClock")) { clock }

    single<AttendanceDatabase> {
        createAttendanceDatabase(
            path = databasePath,
        )
    } onClose { database ->
        database?.close()
    }

    single<Flow<LocalDate>>(
        qualifier = named("attendanceCurrentDates"),
    ) {
        attendanceDates(
            clock = clock,
        )
    }
}
