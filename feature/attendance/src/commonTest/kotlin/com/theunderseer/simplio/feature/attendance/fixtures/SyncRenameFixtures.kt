package com.theunderseer.simplio.feature.attendance.fixtures

import com.theunderseer.simplio.feature.attendance.data.sync.SyncConflictItem
import com.theunderseer.simplio.feature.attendance.data.sync.SyncConflictReview

internal fun testCatalogRenameReview(packageConflict: Boolean): SyncConflictReview {
    val name = if (packageConflict) {
        AttendanceTestData.privateEightClassPackage.name
    } else {
        AttendanceTestData.studentGroups.first().name
    }
    val item = SyncConflictItem(
        key = "catalog:new",
        label = name,
        deviceDetails = emptyList(),
        cloudDetails = emptyList(),
        requiresPackageRename = packageConflict,
        requiresGroupRename = !packageConflict,
        unavailablePackageNames = if (packageConflict) listOf(name) else emptyList(),
        unavailableGroupNames = if (packageConflict) emptyList() else listOf(name),
    )
    return SyncConflictReview("catalog-name-conflict", listOf(item), 1)
}
