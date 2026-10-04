package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType

@Composable
fun StudentRegistrationDialog(
    packageTypes: List<PackageType>,
    onRegister: (String, PackageType) -> Unit,
    onDismiss: () -> Unit,
) {
    var fullName by remember {
        mutableStateOf("")
    }

    var selectedPackage by remember {
        mutableStateOf<PackageType?>(null)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Add Student")
        },
        text = {
            Column {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = {
                        Text("Full name")
                    },
                )

                packageTypes.forEach { packageType ->
                    TextButton(
                        onClick = {
                            selectedPackage = packageType
                        },
                    ) {
                        Text(packageType.name)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled =
                fullName.isNotBlank() &&
                    selectedPackage != null,
                onClick = {
                    onRegister(
                        fullName.trim(),
                        checkNotNull(selectedPackage),
                    )
                },
            ) {
                Text("Register")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
            ) {
                Text("Cancel")
            }
        },
    )
}
