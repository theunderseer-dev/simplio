package com.theunderseer.simplio.feature.attendance.fixtures

import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentGroupEntity
import com.theunderseer.simplio.feature.attendance.domain.model.DefaultStudentGroups
import com.theunderseer.simplio.feature.attendance.domain.model.Student
import com.theunderseer.simplio.feature.attendance.domain.model.StudentGroup

const val TEST_GROUP_A_ID = DefaultStudentGroups.GROUP_A_ID
const val TEST_GROUP_B_ID = DefaultStudentGroups.GROUP_B_ID

fun testStudentGroups(): List<StudentGroup> = DefaultStudentGroups.ALL

fun testStudentGroupEntity(
    id: Int = TEST_GROUP_A_ID,
    name: String = DefaultStudentGroups.GROUP_A.name,
): StudentGroupEntity = StudentGroupEntity(
    id = id,
    name = name,
)

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
