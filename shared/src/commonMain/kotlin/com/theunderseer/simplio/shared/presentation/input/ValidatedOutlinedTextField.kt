package com.theunderseer.simplio.shared.presentation.input

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.theunderseer.simplio.shared.resources.Res
import com.theunderseer.simplio.shared.resources.input_digits_only
import com.theunderseer.simplio.shared.resources.input_email_characters
import com.theunderseer.simplio.shared.resources.input_single_line
import com.theunderseer.simplio.shared.resources.input_time_format
import com.theunderseer.simplio.shared.resources.input_zone_characters
import com.theunderseer.simplio.shared.validation.InputPolicy
import org.jetbrains.compose.resources.stringResource

@Composable
fun ValidatedOutlinedTextField(
    input: ValidatedInputState,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    OutlinedTextField(
        state = input.textFieldState,
        inputTransformation = input.transformation,
        lineLimits = TextFieldLineLimits.SingleLine,
        label = { label() },
        modifier = modifier,
        enabled = enabled,
        isError = isError || input.rejectedEdit,
        trailingIcon = trailingIcon,
        keyboardOptions = keyboardOptions,
        supportingText = if (input.rejectedEdit) {
            { Text(inputRejectionMessage(input.policy)) }
        } else {
            supportingText
        },
    )
}

@Composable
private fun inputRejectionMessage(policy: InputPolicy): String = when (policy) {
    is InputPolicy.SingleLine -> stringResource(Res.string.input_single_line, policy.maxLength)
    InputPolicy.UnsignedInteger -> stringResource(Res.string.input_digits_only)
    InputPolicy.ClockTime -> stringResource(Res.string.input_time_format)
    InputPolicy.TimeZone -> stringResource(Res.string.input_zone_characters)
    InputPolicy.Email -> stringResource(Res.string.input_email_characters)
}
