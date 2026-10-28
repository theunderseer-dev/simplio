package com.theunderseer.simplio.feature.attendance.domain.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone

/** A reusable weekly series. Groups share classes by referring to the same schedule, not by equal times. */
data class ClassSchedule(
    val id: String,
    val name: String,
    val timeZoneId: String,
    val slots: List<WeeklyClassSlot>,
    val startsOn: LocalDate? = null,
    val endsOn: LocalDate? = null,
    val archivedAt: kotlin.time.Instant? = null,
) {
    init {
        requireScheduleId(id)
        require(name.isNotBlank())
        TimeZone.of(timeZoneId)
        require(startsOn == null || endsOn == null || startsOn <= endsOn)
        require(slots.isNotEmpty() && slots.size <= 100)
        require(slots.map { it.id }.distinct().size == slots.size)
        require(slots.map { Triple(it.dayOfWeek, it.startsAt, it.endsAt) }.distinct().size == slots.size)
    }
}

data class WeeklyClassSlot(val id: String, val dayOfWeek: DayOfWeek, val startsAt: LocalTime, val endsAt: LocalTime) {
    init {
        requireScheduleId(id)
        require(startsAt < endsAt) { "A class must end later on the same day" }
        require(startsAt.second == 0 && startsAt.nanosecond == 0)
        require(endsAt.second == 0 && endsAt.nanosecond == 0)
    }
}

internal fun requireScheduleId(id: String) {
    require(id.isNotBlank() && id.length <= 128 && id.all { it.isLetterOrDigit() || it == '-' || it == '_' })
}
