package com.theunderseer.simplio.feature.attendance.domain.model

import com.theunderseer.simplio.shared.validation.isLineControl

data class CreatePackageTypeRequest(
    val name: String,
    val totalClasses: Int,
    val price: Int,
    val validityWeeks: Int?,
    val maxVisitsPerAttendance: Int,
)

enum class PackageTypeValidation { NAME, CLASSES, PRICE, VALIDITY, VISITS }

fun packageTypeValidation(request: CreatePackageTypeRequest): PackageTypeValidation? = when {
    !isValidPackageName(request.name) -> PackageTypeValidation.NAME
    request.totalClasses <= 0 -> PackageTypeValidation.CLASSES
    request.price < 0 -> PackageTypeValidation.PRICE
    request.validityWeeks != null && request.validityWeeks !in 1..(Int.MAX_VALUE / 7) ->
        PackageTypeValidation.VALIDITY
    request.maxVisitsPerAttendance !in 1..2 || request.maxVisitsPerAttendance > request.totalClasses ->
        PackageTypeValidation.VISITS
    else -> null
}

fun displayPackageName(name: String): String = name.trim().split(Regex("\\s+")).joinToString(" ")

fun normalizedPackageName(name: String): String = displayPackageName(name).lowercase()

fun isValidPackageName(name: String): Boolean = displayPackageName(name).let {
    it.isNotEmpty() && it.length <= MAX_PACKAGE_NAME_LENGTH && name.none(::isLineControl)
}

const val MAX_PACKAGE_NAME_LENGTH = 80

sealed interface CreatePackageTypeResult {
    data class Success(val packageType: PackageType) : CreatePackageTypeResult
    data object DuplicateName : CreatePackageTypeResult
    data class Invalid(val reason: PackageTypeValidation) : CreatePackageTypeResult
}
