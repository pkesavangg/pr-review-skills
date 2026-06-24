package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing

/**
 * Multi-line text area input field.
 *
 * Wraps [InputFieldBase] with [singleLine] = false and a minimum height of 118dp
 * (matching the Figma spec). Shares the same theming, validation, and error handling as [AppInput].
 */
@Composable
fun <T> AppTextArea(
    formControl: FormControl<T>?,
    modifier: Modifier = Modifier,
    label: String? = "",
    placeHolder: String = "",
    enabled: Boolean = true,
    readOnly: Boolean = false,
    supportingText: String? = null,
    minHeight: Dp = 118.dp,
    maxLength: Int? = null,
    showCharacterCounter: Boolean = false,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: (() -> Unit)? = null,
    onValueChange: ((T?) -> Unit)? = null,
    nextFocusRequester: FocusRequester? = null,
) {
    InputFieldBase(
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = minHeight),
        formControl = formControl,
        label = label?.lowercase(),
        value = AppInputDefaults.valueToString(AppInputType.TEXT, formControl?.value),
        onValueChange = onValueChange,
        placeHolder = placeHolder,
        enabled = enabled,
        readOnly = readOnly,
        supportingText = supportingText,
        inputType = AppInputType.TEXT,
        maxLength = maxLength,
        showCharacterCounter = showCharacterCounter,
        singleLine = false,
        showTrailingIcon = false,
        onImeAction = onImeAction,
        nextFocusRequester = nextFocusRequester,
    )
}

@PreviewTheme
@Composable
fun AppTextAreaPreview() {
    MeAppTheme {
        val empty = remember { FormControl.create("", emptyList()) }
        val withText = remember { FormControl.create("This is a multi-line note.", emptyList()) }
        val atMax = remember { FormControl.create("x".repeat(280), emptyList()) }
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            // Default state — counter shows 0/280
            AppTextArea(
                formControl = empty,
                label = "notes",
                maxLength = 280,
                showCharacterCounter = true,
            )
            // Filled state — counter shows current count
            AppTextArea(
                formControl = withText,
                label = "notes",
                maxLength = 280,
                showCharacterCounter = true,
            )
            // Max-limit-reached state — counter shows 280/280 in error color
            AppTextArea(
                formControl = atMax,
                label = "notes",
                maxLength = 280,
                showCharacterCounter = true,
            )
        }
    }
}
