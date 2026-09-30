package com.theunderseer.simplio.feature.attendance.data.local.mapper

import com.theunderseer.simplio.feature.attendance.data.local.entity.PackageTypeEntity
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType

fun PackageTypeEntity.toDomain(): PackageType =
    PackageType(
        id = id,
        name = name,
        totalClasses = totalClasses,
        validityWeeks = validityWeeks,
        price = price,
    )
