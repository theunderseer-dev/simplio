package com.theunderseer.simplio.feature.attendance.domain.model

object DefaultStudentGroups {
    val GROUP_A = StudentGroup(
        id = 1,
        name = "Group A",
    )

    val GROUP_B = StudentGroup(
        id = 2,
        name = "Group B",
    )

    val ALL = listOf(GROUP_A, GROUP_B)
}
