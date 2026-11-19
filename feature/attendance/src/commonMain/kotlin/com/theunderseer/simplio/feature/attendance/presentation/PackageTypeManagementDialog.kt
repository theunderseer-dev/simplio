package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.resources.Res
import com.theunderseer.simplio.feature.attendance.resources.class_close
import com.theunderseer.simplio.feature.attendance.resources.class_saving
import com.theunderseer.simplio.feature.attendance.resources.ic_close
import com.theunderseer.simplio.feature.attendance.resources.manage_packages
import com.theunderseer.simplio.feature.attendance.resources.manage_packages_empty
import com.theunderseer.simplio.feature.attendance.resources.manage_packages_one_week
import com.theunderseer.simplio.feature.attendance.resources.manage_packages_terms
import com.theunderseer.simplio.feature.attendance.resources.manage_packages_weeks
import com.theunderseer.simplio.feature.attendance.resources.package_cancel
import com.theunderseer.simplio.feature.attendance.resources.package_delete_action
import com.theunderseer.simplio.feature.attendance.resources.package_delete_button
import com.theunderseer.simplio.feature.attendance.resources.package_delete_confirmation
import com.theunderseer.simplio.feature.attendance.resources.package_delete_failed
import com.theunderseer.simplio.feature.attendance.resources.package_delete_title
import com.theunderseer.simplio.feature.attendance.resources.package_no_expiration
import com.theunderseer.simplio.shared.presentation.components.SimplioActionTone
import com.theunderseer.simplio.shared.presentation.components.SimplioIconButton
import com.theunderseer.simplio.shared.presentation.components.SimplioTextButton
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PackageTypeManagementDialog(
    packageTypes: List<PackageType>,
    deletion: DeletePackageTypeUiState,
    onDelete: (Int) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
) {
    var requestedPackage by remember { mutableStateOf<PackageType?>(null) }
    val saving = deletion == DeletePackageTypeUiState.Saving
    fun dismissConfirmation() {
        requestedPackage = null
        onClear()
    }

    LaunchedEffect(deletion) {
        if (deletion == DeletePackageTypeUiState.Deleted) dismissConfirmation()
    }

    val packToDelete = requestedPackage
    if (packToDelete == null) {
        AlertDialog(
            onDismissRequest = onClose,
            title = { Text(stringResource(Res.string.manage_packages)) },
            text = {
                if (packageTypes.isEmpty()) {
                    Text(stringResource(Res.string.manage_packages_empty))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(packageTypes, key = { it.id }) { pack ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(pack.name, style = MaterialTheme.typography.titleSmall)
                                    val validity = when (val weeks = pack.validityWeeks) {
                                        null -> stringResource(Res.string.package_no_expiration)
                                        1 -> stringResource(Res.string.manage_packages_one_week)
                                        else -> stringResource(Res.string.manage_packages_weeks, weeks.toString())
                                    }
                                    Text(
                                        stringResource(
                                            Res.string.manage_packages_terms,
                                            pack.totalClasses.toString(),
                                            validity,
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                SimplioIconButton(
                                    painter = painterResource(Res.drawable.ic_close),
                                    contentDescription = stringResource(Res.string.package_delete_action, pack.name),
                                    onClick = { requestedPackage = pack },
                                    tone = SimplioActionTone.Destructive,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                SimplioTextButton(onClick = onClose) { Text(stringResource(Res.string.class_close)) }
            },
        )
    } else {
        AlertDialog(
            onDismissRequest = { if (!saving) dismissConfirmation() },
            title = { Text(stringResource(Res.string.package_delete_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.package_delete_confirmation, packToDelete.name))
                    if (deletion == DeletePackageTypeUiState.Failed) {
                        Text(stringResource(Res.string.package_delete_failed), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = {
                SimplioTextButton(onClick = ::dismissConfirmation, enabled = !saving) {
                    Text(stringResource(Res.string.package_cancel))
                }
            },
            confirmButton = {
                SimplioTextButton(onClick = { onDelete(packToDelete.id) }, enabled = !saving) {
                    Text(stringResource(if (saving) Res.string.class_saving else Res.string.package_delete_button))
                }
            },
        )
    }
}
