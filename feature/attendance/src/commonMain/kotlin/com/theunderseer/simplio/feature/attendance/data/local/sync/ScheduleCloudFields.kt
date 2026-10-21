package com.theunderseer.simplio.feature.attendance.data.local.sync

import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.mapper.toDomain
import com.theunderseer.simplio.feature.attendance.data.remote.FirestoreUploadProtocol
import com.theunderseer.simplio.feature.attendance.data.sync.AttendanceCloudDocument
import com.theunderseer.simplio.feature.attendance.domain.model.ClassSchedule
import com.theunderseer.simplio.feature.attendance.domain.model.WeeklyClassSlot
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private fun firestoreArray(values: List<JsonObject>): JsonObject = buildJsonObject {
    put("arrayValue", buildJsonObject { put("values", JsonArray(values)) })
}

internal fun scheduleReferences(ids: List<String>): JsonObject =
    firestoreArray(ids.map(FirestoreUploadProtocol::string))

internal fun AttendanceCloudDocument.scheduleIds(): List<String>? = fields["scheduleIds"]?.let { field ->
    field.jsonObject.getValue("arrayValue").jsonObject["values"]?.jsonArray.orEmpty().map {
        it.jsonObject.getValue("stringValue").jsonPrimitive.also { value -> require(value.isString) }.content
    }.also { require(it.isNotEmpty() && it.size <= 100 && it.distinct().size == it.size) }
}

internal suspend fun scheduleFields(database: AttendanceDatabase, id: String): JsonObject {
    val dao = database.classScheduleDao()
    val schedule = dao.schedules().single { it.id == id }.toDomain(dao.slots())
    val slots = schedule.slots.sortedBy { it.id }.map { slot ->
        val fields = buildJsonObject {
            put("id", FirestoreUploadProtocol.string(slot.id))
            put("dayOfWeek", FirestoreUploadProtocol.integer(slot.dayOfWeek.ordinal + 1))
            put("startMinute", FirestoreUploadProtocol.integer(slot.startsAt.hour * 60 + slot.startsAt.minute))
            put("endMinute", FirestoreUploadProtocol.integer(slot.endsAt.hour * 60 + slot.endsAt.minute))
        }
        buildJsonObject { put("mapValue", buildJsonObject { put("fields", fields) }) }
    }
    return buildJsonObject {
        put("name", FirestoreUploadProtocol.string(schedule.name))
        put("timeZoneId", FirestoreUploadProtocol.string(schedule.timeZoneId))
        schedule.startsOn?.let { put("startsOnEpochDay", FirestoreUploadProtocol.integer(it.toEpochDays())) }
        schedule.endsOn?.let { put("endsOnEpochDay", FirestoreUploadProtocol.integer(it.toEpochDays())) }
        put("slots", firestoreArray(slots))
    }
}

internal fun AttendanceCloudDocument.toSchedule(): ClassSchedule {
    val slots = fields.getValue("slots").jsonObject.getValue("arrayValue").jsonObject
        .getValue("values").jsonArray
    require(slots.size in 1..100)
    return ClassSchedule(
        id = cloudId,
        name = string("name"),
        timeZoneId = string("timeZoneId"),
        slots = slots.map { value ->
            val row = copy(fields = value.jsonObject.getValue("mapValue").jsonObject.getValue("fields").jsonObject)
            val day = row.integer("dayOfWeek").also { require(it in 1..7) }.toInt()
            val start = row.integer("startMinute").also { require(it in 0..1439) }.toInt()
            val end = row.integer("endMinute").also { require(it in 0..1439) }.toInt()
            WeeklyClassSlot(
                row.string("id"),
                DayOfWeek.entries[day - 1],
                LocalTime(start / 60, start % 60),
                LocalTime(end / 60, end % 60),
            )
        },
        startsOn = fields["startsOnEpochDay"]?.let { LocalDate.fromEpochDays(integer("startsOnEpochDay")) },
        endsOn = fields["endsOnEpochDay"]?.let { LocalDate.fromEpochDays(integer("endsOnEpochDay")) },
    )
}
