package com.theunderseer.simplio.feature.attendance.data.local.sync

import com.theunderseer.simplio.feature.attendance.data.sync.AttendanceCloudDocument
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Missing/malformed data must never silently turn a finite package into an unlimited one. */
internal fun AttendanceCloudDocument.validityWeeks(): Int? {
    val value = fields.getValue("validityWeeks").jsonObject
    if ("nullValue" in value) {
        require(integer("schemaVersion") in 3L..4L && value.size == 1 && value["nullValue"] == JsonNull)
        return null
    }
    val weeks = integer("validityWeeks")
    require(weeks in 1L..(Int.MAX_VALUE / 7).toLong())
    return weeks.toInt()
}

internal fun AttendanceCloudDocument.validityExtensions(): Map<String, Int> {
    val field = fields["validityExtensions"] ?: return emptyMap<String, Int>().also {
        require(integer("schemaVersion") != 4L)
    }
    require(type == SyncEntityType.STUDENT_PACKAGE && integer("schemaVersion") == 4L)
    val value = field.jsonObject
    require(value.keys == setOf("mapValue"))
    val map = value.getValue("mapValue").jsonObject["fields"]?.jsonObject.orEmpty()
    require(map.size <= 10_000)
    val grants = map.mapValues { (sessionId, amount) ->
        require(sessionId.isNotBlank())
        val days = amount.jsonObject.getValue("integerValue").jsonPrimitive.content.toInt()
        require(days in 1..14)
        days
    }
    require(grants.values.sumOf { it.toLong() } <= Int.MAX_VALUE)
    require(grants.isEmpty() || validityWeeks() != null)
    return grants
}
