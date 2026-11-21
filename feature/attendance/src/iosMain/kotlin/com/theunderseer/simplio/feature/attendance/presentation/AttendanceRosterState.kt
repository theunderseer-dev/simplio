package com.theunderseer.simplio.feature.attendance.presentation

import com.theunderseer.simplio.feature.attendance.domain.model.AttendanceRoster
import org.jetbrains.compose.resources.StringResource

internal sealed interface AttendanceRosterState {
    data object Loading : AttendanceRosterState
    data class Content(val roster: AttendanceRoster) : AttendanceRosterState
    data class Error(val message: StringResource) : AttendanceRosterState
}
