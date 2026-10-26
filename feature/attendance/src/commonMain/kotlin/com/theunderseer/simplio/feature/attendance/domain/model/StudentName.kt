package com.theunderseer.simplio.feature.attendance.domain.model

import com.theunderseer.simplio.shared.validation.isLineControl

const val MAX_STUDENT_NAME_LENGTH = 200

fun isValidStudentName(name: String): Boolean =
    name.trim().let { it.isNotEmpty() && it.length <= MAX_STUDENT_NAME_LENGTH } && name.none(::isLineControl)

class InvalidStudentName : IllegalArgumentException()
