package com.theunderseer.simplio.feature.attendance.domain.model

fun normalizedGroupName(name: String): String = displayGroupName(name).lowercase()

fun displayGroupName(name: String): String = name.trim().split(Regex("\\s+")).joinToString(" ")

const val MAX_GROUP_NAME_LENGTH = 80

fun isValidGroupName(name: String): Boolean = displayGroupName(name).let {
    it.isNotEmpty() && it.length <= MAX_GROUP_NAME_LENGTH && it.none(Char::isISOControl)
}
