package com.theunderseer.simplio.feature.attendance.domain.model

import kotlinx.datetime.LocalDate

data class InitialAttendance(val date: LocalDate, val visitCount: Int = 1) {
    init {
        require(visitCount > 0) {
            "Visit count must be positive"
        }
    }
}
