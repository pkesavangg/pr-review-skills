package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.greatergoods.meapp.R
import com.greatergoods.meapp.features.common.helper.form.DecimalInputVisualTransformation
import com.greatergoods.meapp.features.common.helper.form.FormField
import com.greatergoods.meapp.theme.MeAppTheme

enum class AppInputType {
    TEXT, PASSWORD, CHECKBOX, DATE_PICKER, NUMBER, TIME_PICKER, DROP_DOWN, BODY_COMP, WEIGHT
}

private object InputFieldTokens {
    val borderRadius = 8.dp
    val height = 56.dp
}

object AppInputDefaults {
    fun visualTransformation(type: AppInputType, allowDecimal: Boolean): VisualTransformation = when(type) {
        AppInputType.PASSWORD -> PasswordVisualTransformation()

        AppInputType.WEIGHT, AppInputType.BODY_COMP -> {
            if (allowDecimal) {
                DecimalInputVisualTransformation(decimalDigits = 1)
            } else {
                DecimalInputVisualTransformation(decimalDigits = 0)
            }
        }
        else -> VisualTransformation.None // Default case for other AppInputTypes
    }

    fun keyboardType(type: AppInputType): KeyboardType = when (type) {
        AppInputType.TEXT -> KeyboardType.Companion.Text
        AppInputType.WEIGHT, AppInputType.BODY_COMP, AppInputType.NUMBER -> KeyboardType.Companion.Number
        AppInputType.PASSWORD -> KeyboardType.Companion.Password
        else -> KeyboardType.Companion.Unspecified
    }

    fun imeAction(type: AppInputType): ImeAction = when (type) {
        AppInputType.PASSWORD -> ImeAction.Companion.Done
        else -> ImeAction.Companion.Next
    }

    fun filterValue(type: AppInputType, value: String): String = when (type) {
        AppInputType.WEIGHT, AppInputType.BODY_COMP -> value.filter { it.isDigit() }
        else -> value
    }
}


@Composable
fun AppInput(
    type: AppInputType,
    formControl: FormField<Any>?,
    name: String,
    label: String,
    placeHolder: String = "",
    enabled: Boolean = true,
    readOnly: Boolean = false,
    supportingText: String? = null,
    modifier: Modifier = Modifier.Companion,
    showTrailingIcon: Boolean = true,
    allowDecimal: Boolean = true,
) {
    InputFieldBase(
        modifier = modifier,
        formControl = formControl,
        name = name,
        label = label,
        value = formControl?.value?.toString() ?: "",
        onValueChange = { newValue ->
            formControl?.parent?.update(name, AppInputDefaults.filterValue(type, newValue))
        },
        placeHolder = placeHolder,
        enabled = enabled,
        readOnly = readOnly,
        supportingText = supportingText,
        inputType = type,
        visualTransformation = AppInputDefaults.visualTransformation(type, allowDecimal),
        keyboardOptions = KeyboardOptions(
            keyboardType = AppInputDefaults.keyboardType(type),
            imeAction = AppInputDefaults.imeAction(type)
        ),
        showTrailingIcon = showTrailingIcon
    )
}


/**
 * Base input composable with full form event support and error handling.
 */
@Composable
fun <T> InputFieldBase(
    modifier: Modifier = Modifier.Companion,
    formControl: FormField<T>? = null,
    name: String = "",
    label: String,
    value: String = "",
    onValueChange: ((String) -> Unit)? = null,
    onFocus: (() -> Unit)? = null,
    onBlur: (() -> Unit)? = null,
    onDone: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    placeHolder: String = "",
    enabled: Boolean = true,
    readOnly: Boolean = false,
    supportingText: String? = null,
    inputType: AppInputType = AppInputType.TEXT,
    showTrailingIcon: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.Companion.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Companion.Default,
    focusRequester: FocusRequester = remember { FocusRequester() },
) {
    val spacing = MeAppTheme.spacing
    val colors = MeAppTheme.colorScheme
    val typography = MeAppTheme.typography
    val labelPadding = spacing.xs
    var statelessValue by remember { mutableStateOf(value) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var isFocused by remember { mutableStateOf(false) }
    val currentOnFocus by rememberUpdatedState(onFocus) // Added
    val currentOnBlur by rememberUpdatedState(onBlur)   // Added
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val isError = formControl?.showError ?: false
    val showErrorIcon = isError && showTrailingIcon && inputType != AppInputType.PASSWORD  // Renamed for clarity in trailingIcon logic
    val showPasswordToggle = inputType == AppInputType.PASSWORD && showTrailingIcon
    // Adjusted showClear logic to not interfere with password toggle if both could be true.
    // Clear icon should not show if it's a password field (it has its own toggle) or if there's an error (error icon takes precedence).
    val showClearButton = (formControl?.value?.toString()?.isNotEmpty() ?: statelessValue.isNotEmpty()) && !isError && inputType != AppInputType.PASSWORD && enabled && !readOnly && showTrailingIcon

    val labelColor = when {
        isError -> colors.error
        !enabled -> colors.subheading.copy(alpha = 0.5f)
        else -> colors.subheading
    }
    val inputTextColor = when {
        !enabled  -> colors.subheading.copy(alpha = 0.5f)
        else -> colors.body
    }
    val placeholderColor = colors.subheading.copy(alpha = 0.5f)
    val backgroundColor = when {
        !enabled  -> colors.secondary.copy(alpha = 0.5f)
        else -> colors.primary
    }
    // Define container colors based on Material Design guidelines
    val containerColor = colors.primary // Default container color
    val disabledContainerColor = colors.secondary.copy(alpha = 0.5f) // Disabled container color
    // As a fallback if errorContainer is not suitable in your theme, colors.surfaceVariant or colors.surface could be used:
    // val errorInputContainerColor = colors.surfaceVariant


    val iconTint = when {
        isError -> colors.error
        !enabled  -> colors.secondaryDisabled
        else -> colors.primaryAction
    }

    // Calculate supporting text height safely
    val supportingTextHeight = if (typography.body3.lineHeight.isSpecified) {
        typography.body3.lineHeight.value.dp
    } else {
        16.dp // Default height if line height is unspecified
    }

    // Update statelessValue when value prop changes
    if (value != statelessValue && formControl == null) {
        statelessValue = value
    }

    val inputValue = formControl?.value?.toString() ?: statelessValue

    fun stringToValue(value: String): T {
        return when (inputType) {
            AppInputType.NUMBER -> when (formControl?.value) {
                is Int -> value.toIntOrNull() as T
                is Long -> value.toLongOrNull() as T
                is Float -> value.toFloatOrNull() as T
                is Double -> value.toDoubleOrNull() as T
                else -> value as T
            }
            else -> value as T
        }
    }

    fun clearValueAndNotify() {
        formControl?.parent?.update(name, stringToValue("") as Any)
        statelessValue = ""
        onValueChange?.invoke("")
    }

    fun togglePasswordVisibility() {
        passwordVisible = !passwordVisible
    }

    val trailingIcon: (@Composable (() -> Unit))? = when {
        showErrorIcon -> {
            @Composable {
                Icon(
                    painter = painterResource(id = R.drawable.ic_close_outlined),
                    contentDescription = "Error",
                    tint = iconTint,
                    modifier = Modifier.Companion
                        .size(24.dp)
                )
            }
        }
        showPasswordToggle -> {
            @Composable {
                val iconResId = if (passwordVisible) R.drawable.ic_eye_close else R.drawable.ic_eye_open
                val contentDescription = if (passwordVisible) "Hide password" else "Show password"
                Icon(
                    painter = painterResource(id = iconResId),
                    contentDescription = contentDescription,
                    tint = iconTint,
                    modifier = Modifier.Companion
                        .size(24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(enabled = enabled) { togglePasswordVisibility() }
                )
            }
        }
        showClearButton -> {
            @Composable {
                Icon(
                    painter = painterResource(id = R.drawable.ic_close_outlined),
                    contentDescription = "Clear",
                    tint = iconTint,
                    modifier = Modifier.Companion
                        .size(20.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                        .clickable(enabled = enabled) { clearValueAndNotify() }
                )
            }
        }
        else -> null
    }

    val inputTransformation = if (inputType == AppInputType.PASSWORD && passwordVisible) {
        VisualTransformation.None
    } else {
        visualTransformation
    }

    Column(modifier = modifier) {
        TextField(
            value = inputValue,
            onValueChange = { newValue ->
                if (formControl != null) {
                    formControl.parent?.update(name, stringToValue(newValue) as Any)
                } else {
                    statelessValue = newValue
                    onValueChange?.invoke(newValue)
                }
            },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(InputFieldTokens.borderRadius),
            modifier = Modifier.Companion
                .background(
                    backgroundColor,
                    androidx.compose.foundation.shape.RoundedCornerShape(InputFieldTokens.borderRadius)
                )
                .border(
                    0.dp,
                    Color.Companion.Transparent,
                    androidx.compose.foundation.shape.RoundedCornerShape(InputFieldTokens.borderRadius)
                )
                .height(InputFieldTokens.height)
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused && isFocused) {
                        currentOnBlur?.invoke() // Changed to use currentOnBlur
                        formControl?.parent?.touched(name)
                        isFocused = false
                    } else if (focusState.isFocused && !isFocused) {
                        currentOnFocus?.invoke() // Changed to use currentOnFocus
                        isFocused = true
                    }
                },
            label = {
                Text(
                    text = label,
                    style = typography.body3,
                    color = labelColor,
                )
            },
            placeholder = {
                Text(
                    text = placeHolder,
                    style = typography.body2,
                    color = placeholderColor,
                )
            },
            trailingIcon = trailingIcon,
            keyboardOptions = keyboardOptions,
            keyboardActions = KeyboardActions(
                onDone = {
                    onDone?.invoke()
                    focusManager.clearFocus()
                    keyboardController?.hide()
                },
                onNext = { onNext?.invoke() }
            ),
            enabled = enabled,
            readOnly = readOnly,
            visualTransformation = inputTransformation,
            isError = isError,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Companion.Transparent,
                unfocusedIndicatorColor = Color.Companion.Transparent,
                disabledIndicatorColor = Color.Companion.Transparent,
                errorIndicatorColor = Color.Companion.Transparent,
                focusedContainerColor = containerColor,
                unfocusedContainerColor = containerColor,
                disabledContainerColor = disabledContainerColor,
                errorContainerColor = containerColor,
                // Set text colors
                focusedTextColor = inputTextColor,
                unfocusedTextColor = inputTextColor,
                disabledTextColor = inputTextColor,
                errorTextColor = inputTextColor, // Use theme's error color for text in error state
                // Set placeholder colors
                focusedPlaceholderColor = placeholderColor,
                unfocusedPlaceholderColor = placeholderColor,
                disabledPlaceholderColor = placeholderColor,
                // Set cursor color
                cursorColor = colors.primaryAction,
                errorCursorColor = colors.error,
            ),
        )
        // Always reserve space for one line below the field for error/supporting text
        Box(
            modifier = Modifier.Companion
                .fillMaxWidth()
                .height(supportingTextHeight) // Use the calculated safe height
                .padding(start = labelPadding, top = 2.dp)
        ) {
            when {
                isError -> Text(
                    formControl.errorMessage ?: "",
                    color = colors.error,
                    style = typography.body3,
                )

                supportingText != null -> Text(
                    supportingText,
                    color = colors.subheading,
                    style = typography.body3,
                )

                else -> Text(
                    " ", // empty space for layout consistency
                    style = typography.body3,
                )
            }
        }
    }
}
