package com.theunderseer.simplio.feature.attendance.data.local.mapper

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Grants keyed by the cancelled session, stored with their purchased package. */
class PackageValidityExtensionsConverter {
    @TypeConverter
    fun encode(value: Map<String, Int>): String = Json.encodeToString(value.toList().sortedBy { it.first }.toMap())

    @TypeConverter
    fun decode(value: String): Map<String, Int> = Json.decodeFromString(value)
}
