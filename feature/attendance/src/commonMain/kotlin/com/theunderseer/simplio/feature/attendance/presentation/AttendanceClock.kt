package com.theunderseer.simplio.feature.attendance.presentation

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Venue dates can change before the device's own midnight. The ViewModel owns this collection. */
internal fun attendanceClockTicks(clock: Clock): Flow<Instant> = flow {
    while (true) {
        emit(clock.now())
        delay(30.seconds)
    }
}
