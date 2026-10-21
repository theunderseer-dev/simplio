package com.theunderseer.simplio.feature.attendance.data.local.mapper

import com.theunderseer.simplio.feature.attendance.data.local.entity.ClassScheduleEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.ClassScheduleSlotEntity
import com.theunderseer.simplio.feature.attendance.domain.model.ClassSchedule
import com.theunderseer.simplio.feature.attendance.domain.model.WeeklyClassSlot
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

internal fun ClassSchedule.toEntity() = ClassScheduleEntity(
    id,
    name,
    timeZoneId,
    startsOn?.toEpochDays(),
    endsOn?.toEpochDays(),
)

internal fun ClassSchedule.slotEntities(): List<ClassScheduleSlotEntity> = slots.map {
    ClassScheduleSlotEntity(
        id,
        it.id,
        it.dayOfWeek.ordinal + 1,
        it.startsAt.hour * 60 + it.startsAt.minute,
        it.endsAt.hour * 60 + it.endsAt.minute,
    )
}

internal fun ClassScheduleEntity.toDomain(slots: List<ClassScheduleSlotEntity>): ClassSchedule = ClassSchedule(
    id,
    name,
    timeZoneId,
    slots.filter { it.scheduleId == id }.map {
        require(it.dayOfWeek in 1..7 && it.startMinute in 0..1439 && it.endMinute in 0..1439)
        WeeklyClassSlot(
            it.id,
            DayOfWeek.entries[it.dayOfWeek - 1],
            LocalTime(it.startMinute / 60, it.startMinute % 60),
            LocalTime(it.endMinute / 60, it.endMinute % 60),
        )
    },
    startsOnEpochDay?.let(LocalDate::fromEpochDays),
    endsOnEpochDay?.let(LocalDate::fromEpochDays),
)
