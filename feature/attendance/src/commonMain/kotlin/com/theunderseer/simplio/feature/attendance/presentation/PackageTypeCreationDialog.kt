package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.theunderseer.simplio.feature.attendance.domain.model.CreatePackageTypeRequest
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.PackageTypeValidation
import com.theunderseer.simplio.feature.attendance.domain.model.normalizedPackageName
import com.theunderseer.simplio.feature.attendance.domain.model.packageTypeValidation
import com.theunderseer.simplio.feature.attendance.resources.Res
import com.theunderseer.simplio.feature.attendance.resources.create_package_title
import com.theunderseer.simplio.feature.attendance.resources.package_allow_two
import com.theunderseer.simplio.feature.attendance.resources.package_cancel
import com.theunderseer.simplio.feature.attendance.resources.package_classes
import com.theunderseer.simplio.feature.attendance.resources.package_create
import com.theunderseer.simplio.feature.attendance.resources.package_duplicate_name
import com.theunderseer.simplio.feature.attendance.resources.package_invalid_classes
import com.theunderseer.simplio.feature.attendance.resources.package_invalid_name
import com.theunderseer.simplio.feature.attendance.resources.package_invalid_price
import com.theunderseer.simplio.feature.attendance.resources.package_invalid_validity
import com.theunderseer.simplio.feature.attendance.resources.package_invalid_visits
import com.theunderseer.simplio.feature.attendance.resources.package_name
import com.theunderseer.simplio.feature.attendance.resources.package_no_expiration
import com.theunderseer.simplio.feature.attendance.resources.package_price
import com.theunderseer.simplio.feature.attendance.resources.package_save_failed
import com.theunderseer.simplio.feature.attendance.resources.package_saving
import com.theunderseer.simplio.feature.attendance.resources.package_two_hint
import com.theunderseer.simplio.feature.attendance.resources.package_validity
import com.theunderseer.simplio.feature.attendance.resources.package_validity_hint
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PackageTypeCreationDialog(
    packageTypes: List<PackageType>,
    state: CreatePackageTypeUiState,
    onConfirm: (CreatePackageTypeRequest) -> Unit,
    onDismiss: () -> Unit,
    onClearError: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var classes by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var weeks by remember { mutableStateOf("") }
    var noExpiration by remember { mutableStateOf(false) }
    var allowTwo by remember { mutableStateOf(false) }
    val saving = state == CreatePackageTypeUiState.Saving
    val count = classes.toIntOrNull()
    val amount = price.toIntOrNull()
    val validity = weeks.toIntOrNull()
    val request = if (count != null && amount != null && (noExpiration || validity != null)) {
        CreatePackageTypeRequest(name, count, amount, if (noExpiration) null else validity, if (allowTwo) 2 else 1)
    } else {
        null
    }
    val duplicate = name.isNotBlank() &&
        packageTypes.any {
            normalizedPackageName(it.name) == normalizedPackageName(name)
        }
    val localError = when {
        name.isNotEmpty() &&
            packageTypeValidation(
                CreatePackageTypeRequest(name, 1, 0, null, 1),
            ) == PackageTypeValidation.NAME -> PackageTypeValidation.NAME
        classes.isNotEmpty() && (count == null || count <= 0) -> PackageTypeValidation.CLASSES
        price.isNotEmpty() && (amount == null || amount < 0) -> PackageTypeValidation.PRICE
        !noExpiration && weeks.isNotEmpty() && (validity == null || validity !in 1..(Int.MAX_VALUE / 7)) ->
            PackageTypeValidation.VALIDITY
        allowTwo && count != null && count < 2 -> PackageTypeValidation.VISITS
        else -> null
    }
    val message = when {
        duplicate || state == CreatePackageTypeUiState.DuplicateName -> Res.string.package_duplicate_name
        localError != null -> localError.messageResource()
        state is CreatePackageTypeUiState.Invalid -> state.reason.messageResource()
        state == CreatePackageTypeUiState.SaveFailed -> Res.string.package_save_failed
        else -> null
    }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        modifier = Modifier.widthIn(max = 560.dp),
        title = { Text(stringResource(Res.string.create_package_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        onClearError()
                    },
                    label = { Text(stringResource(Res.string.package_name)) },
                    singleLine = true,
                    enabled = !saving,
                    isError = duplicate || localError == PackageTypeValidation.NAME,
                    modifier = Modifier.fillMaxWidth().testTag("create-package-name"),
                )
                PackageNumberField(classes, Res.string.package_classes, "create-package-classes", saving) {
                    classes = it
                    onClearError()
                }
                PackageNumberField(price, Res.string.package_price, "create-package-price", saving) {
                    price = it
                    onClearError()
                }
                PackageOption(noExpiration, !saving, Res.string.package_no_expiration, "create-package-no-expiration") {
                    noExpiration = it
                    onClearError()
                }
                if (!noExpiration) {
                    PackageNumberField(weeks, Res.string.package_validity, "create-package-weeks", saving) {
                        weeks = it
                        onClearError()
                    }
                    Text(
                        stringResource(Res.string.package_validity_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PackageOption(allowTwo, !saving, Res.string.package_allow_two, "create-package-allow-two") {
                    allowTwo = it
                    onClearError()
                }
                if (allowTwo) {
                    Text(stringResource(Res.string.package_two_hint), style = MaterialTheme.typography.bodySmall)
                }
                message?.let {
                    Text(
                        stringResource(it),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("create-package-error"),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { request?.let(onConfirm) },
                enabled = !saving && !duplicate && request != null && packageTypeValidation(request) == null,
                modifier = Modifier.testTag("create-package-confirm"),
            ) {
                Text(stringResource(if (saving) Res.string.package_saving else Res.string.package_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(Res.string.package_cancel)) }
        },
    )
}

@Composable
private fun PackageNumberField(
    value: String,
    label: StringResource,
    tag: String,
    saving: Boolean,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        enabled = !saving,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}

@Composable
private fun PackageOption(
    checked: Boolean,
    enabled: Boolean,
    label: StringResource,
    tag: String,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag)
            .toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, enabled = enabled, onCheckedChange = null)
        Text(
            stringResource(label),
            modifier = Modifier.padding(start = 8.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f),
        )
    }
}

private fun PackageTypeValidation.messageResource(): StringResource = when (this) {
    PackageTypeValidation.NAME -> Res.string.package_invalid_name
    PackageTypeValidation.CLASSES -> Res.string.package_invalid_classes
    PackageTypeValidation.PRICE -> Res.string.package_invalid_price
    PackageTypeValidation.VALIDITY -> Res.string.package_invalid_validity
    PackageTypeValidation.VISITS -> Res.string.package_invalid_visits
}
