package com.theunderseer.simplio.feature.attendance.data.local.sync

import com.theunderseer.simplio.feature.attendance.data.sync.AttendanceCloudDocument
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject

/** Missing/malformed data must never silently turn a finite package into an unlimited one. */
internal fun AttendanceCloudDocument.validityWeeks(): Int? {
    val value = fields.getValue("validityWeeks").jsonObject
    if ("nullValue" in value) {
        require(integer("schemaVersion") == 3L && value.size == 1 && value["nullValue"] == JsonNull)
        return null
    }
    val weeks = integer("validityWeeks")
    require(weeks in 1L..(Int.MAX_VALUE / 7).toLong())
    return weeks.toInt()
}
