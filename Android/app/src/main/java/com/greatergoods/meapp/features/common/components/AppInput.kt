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
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.theme.MeAppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.Arrangement
import com.greatergoods.meapp.theme.MeAppTheme.borderRadius
import com.greatergoods.meapp.theme.MeAppTheme.colorScheme
import com.greatergoods.meapp.theme.MeAppTheme.spacing
import com.greatergoods.meapp.theme.MeAppTheme.typography

enum class AppInputType {
    TEXT,
    PASSWORD,
    NUMBER,
    BODY_COMP,
    WEIGHT,
}

object AppInputDefaults {
    fun visualTransformation(
        type: AppInputType,
        allowDecimal: Boolean,
    ): VisualTransformation =
        when (type) {
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

    fun keyboardType(type: AppInputType): KeyboardType =
        when (type) {
            AppInputType.TEXT -> KeyboardType.Companion.Text
            AppInputType.WEIGHT, AppInputType.BODY_COMP, AppInputType.NUMBER -> KeyboardType.Companion.Number
            AppInputType.PASSWORD -> KeyboardType.Companion.Password
            else -> KeyboardType.Companion.Unspecified
        }

    fun imeAction(type: AppInputType): ImeAction =
        when (type) {
            AppInputType.PASSWORD -> ImeAction.Companion.Done
            else -> ImeAction.Companion.Next
        }

    fun stringToValue(type: AppInputType, value: String, formControl: FormControl<*>?): Any? = when (type) {
        AppInputType.NUMBER, AppInputType.WEIGHT, AppInputType.BODY_COMP -> when (formControl?.value) {
            is Int -> value.toIntOrNull()
            is Long -> value.toLongOrNull()
            is Float -> value.toFloatOrNull()
            is Double -> value.toDoubleOrNull()
            else -> value
        }
        else -> value
    }

    fun valueToString(type: AppInputType, value: Any?): String = when (type) {
        AppInputType.NUMBER, AppInputType.WEIGHT, AppInputType.BODY_COMP -> value?.toString() ?: ""
        else -> value?.toString() ?: ""
    }

    fun filterValue(type: AppInputType, value: String): String = when (type) {
        AppInputType.WEIGHT, AppInputType.BODY_COMP -> value.filter { it.isDigit() }
        else -> value
    }
}

@Composable
fun <T> AppInput(
    formControl: FormControl<T>?,
    modifier: Modifier = Modifier,
    type: AppInputType = AppInputType.TEXT,
    label: String? = "",
    placeHolder: String = "",
    enabled: Boolean = true,
    readOnly: Boolean = false,
    supportingText: String? = null,
    showTrailingIcon: Boolean = true,
    allowDecimal: Boolean = true,
    onValueChange: ((T?) -> Unit)? = null,
) {
    val onInputChange: (String) -> Unit = { value ->
        val filtered = AppInputDefaults.filterValue(type, value)
        val newValue = AppInputDefaults.stringToValue(type, filtered, formControl) as T?
        if (newValue != null) {
            formControl?.onValueChange(newValue)
            onValueChange?.let { it(newValue) }
        }
    }
    val visualTransformation = AppInputDefaults.visualTransformation(type, allowDecimal)
    val keyboardOptions = KeyboardOptions(
        keyboardType = AppInputDefaults.keyboardType(type),
        imeAction = AppInputDefaults.imeAction(type),
    )
    InputFieldBase(
        modifier = modifier,
        formControl = formControl,
        label = label,
        value = AppInputDefaults.valueToString(type, formControl?.value),
        onValueChange = onInputChange,
        placeHolder = placeHolder,
        enabled = enabled,
        readOnly = readOnly,
        supportingText = supportingText,
        inputType = type,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        showTrailingIcon = showTrailingIcon,
    )
}

/**
 * Base input composable with full form event support and error handling.
 */
@Composable
fun <T> InputFieldBase(
    modifier: Modifier = Modifier,
    formControl: FormControl<T>? = null,
    label: String? = null,
    value: String = "",
    placeHolder: String = "",
    enabled: Boolean = true,
    inputType: AppInputType = AppInputType.TEXT,
    readOnly: Boolean = false,
    supportingText: String? = null,
    showTrailingIcon: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onFocus: (() -> Unit)? = null,
    onBlur: (() -> Unit)? = null,
    onDone: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    onValueChange: ((String) -> Unit)? = null,
) {
    var isFocused by remember { mutableStateOf(false) }
    val currentOnFocus by rememberUpdatedState(onFocus)
    val currentOnBlur by rememberUpdatedState(onBlur)
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    val isError = formControl?.error.isNullOrBlank().not()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isPassword = inputType == AppInputType.PASSWORD
    val showPasswordToggle = isPassword && showTrailingIcon
    val showClearButton =
        formControl?.value?.toString()?.isNotEmpty() == true && isPassword &&
                enabled && !readOnly && showTrailingIcon

    val labelColor =
        when {
            isError -> colorScheme.error
            else -> colorScheme.subheading
        }
    val inputTextColor = when {
        !enabled -> colorScheme.subheading
        else -> colorScheme.body
    }
    val placeholderColor = colorScheme.secondary
    val backgroundColor =
        when {
            !enabled -> colorScheme.secondaryDisabled
            else -> colorScheme.primary
        }
    // Define container colors based on Material Design guidelines
    val containerColor = colorScheme.primary // Default container color
    val disabledContainerColor = colorScheme.secondaryDisabled // Disabled container color
    // As a fallback if errorContainer is not suitable in your theme, colors.surfaceVariant or colors.surface could be used:
    // val errorInputContainerColor = colors.surfaceVariant

    val iconTint =
        when {
            isError -> colorScheme.error
            !enabled -> colorScheme.secondaryDisabled
            else -> colorScheme.primaryAction
        }

    val inputValue = value

    fun clearValueAndNotify() {
        val clearedValue = AppInputDefaults.stringToValue(inputType, "", formControl) as T?
        if (clearedValue != null) {
            formControl?.onValueChange(clearedValue)
            onValueChange?.invoke("")
        }
    }

    fun togglePasswordVisibility() {
        passwordVisible = !passwordVisible
    }

    val trailingIcon: (@Composable (() -> Unit))? =
        when {
            showPasswordToggle -> {
                @Composable {
                    val iconResId = if (passwordVisible) R.drawable.ic_eye_close else R.drawable.ic_eye_open
                    val contentDescription = if (passwordVisible) "Hide password" else "Show password"
                    Icon(
                        painter = painterResource(id = iconResId),
                        contentDescription = contentDescription,
                        tint = iconTint,
                        modifier =
                            Modifier.Companion
                                .size(24.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable(enabled = enabled) { togglePasswordVisibility() },
                    )
                }
            }

            showClearButton -> {
                @Composable {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close_outlined),
                        contentDescription = "Clear",
                        tint = iconTint,
                        modifier =
                            Modifier.Companion
                                .size(20.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable(enabled = !isError) { clearValueAndNotify() },
                    )
                }
            }

            else -> null
        }

    val inputTransformation =
        if (inputType == AppInputType.PASSWORD && passwordVisible) {
            VisualTransformation.None
        } else {
            visualTransformation
        }

    Column(modifier = modifier) {
        TextField(
            value = inputValue,
            onValueChange = { newValue ->
                val castValue = AppInputDefaults.stringToValue(inputType, newValue, formControl) as T?
                if (formControl != null && castValue != null) {
                    formControl.onValueChange(castValue)
                } else {
                    onValueChange?.invoke(newValue)
                }
            },
            shape =
                androidx.compose.foundation.shape
                    .RoundedCornerShape(borderRadius.sm),
            modifier =
                Modifier.Companion
                    .background(
                        backgroundColor,
                        androidx.compose.foundation.shape
                            .RoundedCornerShape(borderRadius.sm),
                    ).border(
                        0.dp,
                        Color.Companion.Transparent,
                        androidx.compose.foundation.shape
                            .RoundedCornerShape(borderRadius.sm),
                    ).height(56.dp)
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        if (!focusState.isFocused && isFocused) {
                            currentOnBlur?.invoke()
                            formControl?.onBlur() // handle touched on blur
                            isFocused = false
                        } else if (focusState.isFocused && !isFocused) {
                            currentOnFocus?.invoke()
                            isFocused = true
                        }
                    },
            label = {
                label?.let {
                    Text(
                        text = label,
                        style = typography.body3,
                        color = labelColor,
                    )
                }
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
            keyboardActions =
                KeyboardActions(
                    onDone = {
                        onDone?.invoke()
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    },
                    onNext = { onNext?.invoke() },
                ),
            enabled = enabled,
            readOnly = readOnly,
            visualTransformation = inputTransformation,
            isError = isError,
            colors =
                TextFieldDefaults.colors(
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
                    disabledTextColor = colorScheme.subheading,
                    errorTextColor = inputTextColor,
                    // Set placeholder colors
                    focusedPlaceholderColor = placeholderColor,
                    unfocusedPlaceholderColor = placeholderColor,
                    disabledPlaceholderColor = placeholderColor,
                    // Set cursor color
                    cursorColor = colorScheme.primaryAction,
                    errorCursorColor = colorScheme.error,
                ),
        )
        // Reserve space for one line below the field for error/supporting text
        Box(
            modifier =
                Modifier.Companion
                    .fillMaxWidth()
                    .height(spacing.sm) // Use the calculated safe height
                    .padding(start = spacing.xs, top = 2.dp),
        ) {
            when {
                isError ->
                    Text(
                        formControl.error ?: "",
                        color = colorScheme.error,
                        style = typography.body3,
                    )

                supportingText != null ->
                    Text(
                        supportingText,
                        color = colorScheme.subheading,
                        style = typography.body3,
                    )

                else ->
                    Text(
                        " ", // empty space for layout consistency
                        style = typography.body3,
                    )
            }
        }
    }
}

@PreviewTheme
@Composable
fun AppInputPreview() {
    MeAppTheme {
        val fakeScope = rememberCoroutineScope()
        val normal = remember { FormControl("Input", emptyList(), emptyList(), fakeScope) }
        val error = remember { FormControl("Input", listOf({ "This field is required" }), emptyList(), fakeScope) }
        val password = remember { FormControl("", listOf({ "Password must be at least 8 characters" }), emptyList(), fakeScope) }
        val disabled = remember { FormControl("", emptyList(), emptyList(), fakeScope) }
        val focused = remember { FormControl("", emptyList(), emptyList(), fakeScope) }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(16.dp)) {
            AppInput(formControl = normal, label = "Normal Input", type = AppInputType.TEXT)
            AppInput(formControl = focused, label = "Focused Input", type = AppInputType.TEXT)
            AppInput(formControl = error, label = "Error Input", type = AppInputType.TEXT, supportingText = "supporting text")
            AppInput(formControl = password, label = "Password with Error", type = AppInputType.PASSWORD)
            AppInput(formControl = disabled, label = "Disabled Input", type = AppInputType.TEXT, enabled = false)
        }
    }
}


