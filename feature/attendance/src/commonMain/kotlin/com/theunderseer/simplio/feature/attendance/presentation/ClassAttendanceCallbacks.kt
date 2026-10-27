package com.theunderseer.simplio.feature.attendance.presentation

import com.theunderseer.simplio.feature.attendance.domain.model.PackageType

data class ClassAttendanceCallbacks(
    val open: (String) -> Unit,
    val close: () -> Unit,
    val save: (Map<Int, Int>, Boolean) -> Unit,
    val select: (Int, Int) -> Unit,
    val addStudent: () -> Unit,
    val renewStudent: (Int) -> Unit,
    val dismissStudentAction: () -> Unit,
    val showPackageCreation: (Boolean) -> Unit,
    val register: (String, Int, PackageType, Int, Boolean, String) -> Unit,
    val renew: (PackageType, Int, Boolean, String) -> Unit,
)
