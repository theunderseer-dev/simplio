package com.theunderseer.simplio.feature.attendance.domain.model

import kotlinx.datetime.LocalDate

data class ClassHistoryPage(val entries: List<ClassHistoryEntry>, val nextBeforeDate: LocalDate?)
