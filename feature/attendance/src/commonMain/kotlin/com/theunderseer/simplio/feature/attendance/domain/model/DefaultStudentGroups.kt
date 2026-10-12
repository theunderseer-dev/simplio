package com.theunderseer.simplio.feature.attendance.domain.model

object DefaultStudentGroups {
    const val GROUP_A_ID = 1
    const val GROUP_B_ID = 2

    val GROUP_A = StudentGroup(
        id = GROUP_A_ID,
        name = "Group A",
    )

    val GROUP_B = StudentGroup(
        id = GROUP_B_ID,
        name = "Group B",
    )

    val ALL = listOf(GROUP_A, GROUP_B)
}
