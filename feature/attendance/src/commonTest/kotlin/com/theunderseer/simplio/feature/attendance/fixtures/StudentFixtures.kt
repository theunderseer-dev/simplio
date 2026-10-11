package com.theunderseer.simplio.feature.attendance.fixtures

import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentGroupEntity
import com.theunderseer.simplio.feature.attendance.domain.model.Student
import com.theunderseer.simplio.feature.attendance.domain.model.StudentGroup

const val TEST_GROUP_A_ID = 1
const val TEST_GROUP_B_ID = 2

fun testStudent(id: Int = 1, fullName: String = "Anna Smith", groupId: Int = TEST_GROUP_A_ID): Student = Student(
    id = id,
    fullName = fullName,
    groupId = groupId,
)

fun testStudentEntity(id: Int = 0, fullName: String = "Anna Smith", groupId: Int = TEST_GROUP_A_ID): StudentEntity =
    StudentEntity(
        id = id,
        fullName = fullName,
        groupId = groupId,
    )

fun testStudentGroupEntity(id: Int = TEST_GROUP_A_ID, name: String = "Group A"): StudentGroupEntity =
    StudentGroupEntity(
        id = id,
        name = name,
    )

fun testStudentGroups(): List<StudentGroup> = listOf(
    StudentGroup(id = TEST_GROUP_A_ID, name = "Group A"),
    StudentGroup(id = TEST_GROUP_B_ID, name = "Group B"),
)
