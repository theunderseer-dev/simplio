package com.theunderseer.simplio.shared.fixtures

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.theunderseer.simplio.shared.presentation.input.ValidatedOutlinedTextField
import com.theunderseer.simplio.shared.presentation.input.rememberValidatedInput
import com.theunderseer.simplio.shared.validation.InputPolicy

@Composable
internal fun ValidatedInputFormForTest(
    initialText: String = InputTestData.number,
    policy: InputPolicy = InputPolicy.UnsignedInteger,
    onSave: (String) -> Unit = {},
) {
    val input = rememberValidatedInput(initialText, policy)
    val valid = input.text.toIntOrNull()?.let { it > 0 } == true
    MaterialTheme {
        Column {
            ValidatedOutlinedTextField(input, { Text("Number") }, Modifier.testTag("number"))
            TextButton(onClick = { onSave(input.text) }, enabled = valid && !input.rejectedEdit) { Text("Save") }
        }
    }
}
