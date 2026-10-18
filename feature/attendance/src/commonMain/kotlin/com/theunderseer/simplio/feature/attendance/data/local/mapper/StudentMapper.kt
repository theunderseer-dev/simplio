package com.theunderseer.simplio.feature.attendance.data.local.mapper

import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentEntity
import com.theunderseer.simplio.feature.attendance.domain.model.Student

fun StudentEntity.toDomain(): Student = Student(
    id = id,
    fullName = fullName,
    groupId = groupId,
    note = note,
)
