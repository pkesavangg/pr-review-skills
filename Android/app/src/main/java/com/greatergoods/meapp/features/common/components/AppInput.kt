package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.features.common.helper.form.DecimalInputVisualTransformation
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeAppTheme.borderRadius
import com.greatergoods.meapp.theme.MeAppTheme.colorScheme
import com.greatergoods.meapp.theme.MeAppTheme.typography
import com.greatergoods.meapp.features.common.strings.AppInputStrings

enum class AppInputType {
    TEXT,
    PASSWORD,
    NUMBER,
    WEIGHT,
    BODY_COMP,
    BODY_COMP_DECIMAL
}

object AppInputDefaults {
    fun visualTransformation(
        type: AppInputType,
    ): VisualTransformation =
        when (type) {
            AppInputType.PASSWORD -> PasswordVisualTransformation()

            AppInputType.WEIGHT, AppInputType.BODY_COMP_DECIMAL -> DecimalInputVisualTransformation(decimalDigits = 1)

            else -> VisualTransformation.None // Default case for other AppInputTypes
        }

    fun keyboardType(type: AppInputType): KeyboardType =
        when (type) {
            AppInputType.TEXT -> KeyboardType.Text
            AppInputType.NUMBER, AppInputType.WEIGHT, AppInputType.BODY_COMP, AppInputType.BODY_COMP_DECIMAL
                -> KeyboardType.Number
            AppInputType.PASSWORD -> KeyboardType.Password
            else -> KeyboardType.Unspecified
        }

    fun imeAction(type: AppInputType): ImeAction =
        when (type) {
            AppInputType.PASSWORD -> ImeAction.Done
            else -> ImeAction.Next
        }

    fun <T> stringToValue(
        type: AppInputType,
        value: String,
        formControl: FormControl<*>?,
    ): T? =
        when (type) {
            AppInputType.NUMBER, AppInputType.WEIGHT, AppInputType.BODY_COMP, AppInputType.BODY_COMP_DECIMAL ->
                when (formControl?.value) {
                    is Int -> value.toIntOrNull()
                    is Long -> value.toLongOrNull()
                    is Float -> value.toFloatOrNull()
                    is Double -> value.toDoubleOrNull()
                    else -> value
                }

            else -> value
        } as T?

    fun <T> valueToString(
        type: AppInputType,
        value: T?,
    ): String =
        when (type) {
            AppInputType.NUMBER, AppInputType.WEIGHT, AppInputType.BODY_COMP -> value?.toString() ?: ""
            else -> value?.toString() ?: ""
        }

    fun filterValue(
        type: AppInputType,
        value: String,
    ): String =
        when (type) {
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
    onValueChange: ((T?) -> Unit)? = null,
) {
    val visualTransformation = AppInputDefaults.visualTransformation(type)
    val keyboardOptions =
        KeyboardOptions(
            keyboardType = AppInputDefaults.keyboardType(type),
            imeAction = AppInputDefaults.imeAction(type),
        )
    InputFieldBase(
        modifier = modifier,
        formControl = formControl,
        label = label,
        value = AppInputDefaults.valueToString(type, formControl?.value),
        onValueChange = onValueChange,
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
    onValueChange: ((T?) -> Unit)? = null,
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
        formControl?.value?.toString()?.isNotEmpty() == true &&
            !isPassword &&
            enabled &&
            !readOnly &&
            showTrailingIcon

    val inputTextColor =
        when {
            !enabled -> colorScheme.subheading
            else -> colorScheme.body
        }

    val inputValue = value

    val onInputChange: (String) -> Unit = { newValue ->
        if (onValueChange != null) {
            onValueChange(newValue as T?)
        } else {
            val filtered = AppInputDefaults.filterValue(inputType, newValue)
            val convertedValue = AppInputDefaults.stringToValue(inputType, filtered, formControl) as T?
            if (convertedValue != null) {
                formControl?.onValueChange(convertedValue)
            }
        }
    }

    fun clearValueAndNotify() {
        val newValue = ""
        val clearedValue = AppInputDefaults.stringToValue(inputType, newValue, formControl) as T?
        if (clearedValue != null) {
            formControl?.onValueChange(clearedValue)
            onValueChange?.invoke(clearedValue)
        }
    }

    val iconColor = if (isError) AppIconType.Danger else AppIconType.Primary
    val trailingIcon: (@Composable (() -> Unit))? =
        when {
            showPasswordToggle -> {
                @Composable {
                    val iconResId = if (passwordVisible) AppIcons.Default.EyeClosed else AppIcons.Default.EyeOpened
                    val contentDescription = if (passwordVisible) "Hide password" else "Show password"
                    AppIcon(
                        id = iconResId,
                        contentDescription = contentDescription,
                        type = iconColor,
                        onClick = { passwordVisible = !passwordVisible },
                    )
                }
            }

            showClearButton -> {
                @Composable {
                    AppIcon(
                        AppIcons.Outlined.Close,
                        contentDescription = "Clear",
                        type = iconColor,
                        onClick = { clearValueAndNotify() },
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

    TextField(
        value = inputValue,
        onValueChange = onInputChange,
        modifier = modifier
            .fillMaxWidth()
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
                    color =  if (isError) colorScheme.textError else colorScheme.subheading,
                )
            }
        },
        placeholder = {
            Text(
                text = placeHolder,
                style = typography.body2,
                color = colorScheme.secondaryActionDisabled,
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
            onNext = { onNext?.invoke() },
        ),
        enabled = enabled,
        readOnly = readOnly,
        visualTransformation = inputTransformation,
        isError = isError,
        shape = RoundedCornerShape(borderRadius.sm),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
            focusedContainerColor =  colorScheme.primary,
            unfocusedContainerColor =  colorScheme.primary,
            disabledContainerColor = colorScheme.secondaryActionDisabled,
            errorContainerColor =  colorScheme.primary,
            focusedTextColor = inputTextColor,
            unfocusedTextColor = inputTextColor,
            disabledTextColor = colorScheme.subheading,
            errorTextColor = inputTextColor,
            focusedPlaceholderColor = colorScheme.secondaryActionDisabled,
            unfocusedPlaceholderColor = colorScheme.secondaryActionDisabled,
            disabledPlaceholderColor = colorScheme.secondaryActionDisabled,
            cursorColor = colorScheme.primaryAction,
            errorCursorColor = colorScheme.textError,
        ),
        supportingText = {
            when {
                isError ->
                    Text(
                        formControl.error ?: "",
                        color = colorScheme.textError,
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
                        AppInputStrings.EmptySpace,
                        style = typography.body3,
                    )
            }
        },
    )
}

@PreviewTheme
@Composable
fun AppInputPreview() {
    MeAppTheme {
       val fakeScope = rememberCoroutineScope()
       val normal = remember { FormControl("Input", emptyList(), emptyList(), fakeScope) }
       val error = remember { FormControl("Input", listOf({ "This field is required" }), emptyList(), fakeScope) }
       val password =
           remember { FormControl("", listOf({ "Password must be at least 8 characters" }), emptyList(), fakeScope) }
       val disabled = remember { FormControl("", emptyList(), emptyList(), fakeScope) }
       val focused = remember { FormControl("", emptyList(), emptyList(), fakeScope) }
       Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(16.dp)) {
           AppInput(formControl = normal, label = "Normal Input", type = AppInputType.TEXT)
           AppInput(formControl = focused, label = "Focused Input", type = AppInputType.TEXT)
           AppInput(
               formControl = error,
               label = "Error Input",
               type = AppInputType.TEXT,
               supportingText = "supporting text",
           )
           AppInput(formControl = password, label = "Password with Error", type = AppInputType.PASSWORD)
           AppInput(formControl = disabled, label = "Disabled Input", type = AppInputType.TEXT, enabled = false)
       }
    }
}
