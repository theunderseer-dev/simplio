package com.theunderseer.simplio.feature.attendance.presentation

import com.theunderseer.simplio.feature.attendance.resources.Res
import com.theunderseer.simplio.feature.attendance.resources.class_add_package
import com.theunderseer.simplio.feature.attendance.resources.class_add_student
import com.theunderseer.simplio.feature.attendance.resources.ios_add_schedule
import org.jetbrains.compose.resources.StringResource

internal enum class IosAttendanceQuickAction(val label: StringResource) {
    Student(Res.string.class_add_student),
    Package(Res.string.class_add_package),
    Schedule(Res.string.ios_add_schedule),
}
