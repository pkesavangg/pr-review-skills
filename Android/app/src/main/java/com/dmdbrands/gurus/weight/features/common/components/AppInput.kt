package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.LocalAutofillHighlightColor
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.dmdbrands.gurus.weight.features.common.helper.form.DecimalInputVisualTransformation
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.common.strings.AppInputStrings
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.borderRadius
import com.dmdbrands.gurus.weight.theme.MeTheme.colorScheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing
import com.dmdbrands.gurus.weight.theme.MeTheme.typography

enum class AppInputType {
    TEXT,
    EMAIL,
    PASSWORD,
    NUMBER,

    /**
     * Input type used for body composition metrics (e.g., weight, body fat, muscle mass).
     * Typically accepts decimal values with specific validation rules.
     */
    BODY_COMP,
    NUMERIC_STRING,
}

object AppInputDefaults {
    fun visualTransformation(type: AppInputType): VisualTransformation =
        when (type) {
            AppInputType.PASSWORD -> PasswordVisualTransformation()

            AppInputType.BODY_COMP ->
                DecimalInputVisualTransformation(
                    decimalDigits = 1,
                )

            else -> VisualTransformation.None // Default case for other AppInputTypes
        }

    fun keyboardType(type: AppInputType): KeyboardType =
        when (type) {
            AppInputType.TEXT -> KeyboardType.Text
            AppInputType.EMAIL -> KeyboardType.Email
            AppInputType.NUMBER, AppInputType.BODY_COMP, AppInputType.NUMERIC_STRING
            -> KeyboardType.Number
            AppInputType.PASSWORD -> KeyboardType.Password
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
            AppInputType.NUMBER, AppInputType.BODY_COMP ->
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
            AppInputType.NUMBER, AppInputType.BODY_COMP ->
                value?.toString()
                    ?: ""

            else -> value?.toString() ?: ""
        }

    fun filterValue(
        type: AppInputType,
        value: String,
    ): String =
        when (type) {
            AppInputType.BODY_COMP -> value.filter { it.isDigit() }
            else -> value
        }
}

/**
 * Manages focus for a group of input fields.
 */
class InputFocusManager {
    private val focusRequesters = mutableListOf<FocusRequester>()

    fun register(requester: FocusRequester): Int {
        focusRequesters.add(requester)
        return focusRequesters.lastIndex
    }

    fun unregister(requester: FocusRequester) {
        focusRequesters.remove(requester)
    }

    fun focusNext(current: FocusRequester) {
        val idx = focusRequesters.indexOf(current)
        if (idx >= 0 && idx < focusRequesters.lastIndex) {
            focusRequesters[idx + 1].requestFocus()
        }
    }

    fun focusPrevious(current: FocusRequester) {
        val idx = focusRequesters.indexOf(current)
        if (idx > 0) {
            focusRequesters[idx - 1].requestFocus()
        }
    }

    fun clearAllFocus() {
        focusRequesters.forEach { it.freeFocus() }
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
    showOutline: Boolean = false,
    supportingText: String? = null,
    showTrailingIcon: Boolean = true,
    showTrailingIconAlways: Boolean = false,
    trailingIconId: Int = AppIcons.Outlined.Close,
    onValueChange: ((T?) -> Unit)? = null,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null,
    onTrailingAction: (() -> Unit)? = null,
    nextFocusRequester: FocusRequester? = null,
) {
    val visualTransformation = AppInputDefaults.visualTransformation(type)
    val keyboardOptions =
        KeyboardOptions(
            keyboardType = AppInputDefaults.keyboardType(type),
            imeAction = imeAction,
        )
    CompositionLocalProvider(LocalAutofillHighlightColor provides Color.Transparent) {
        InputFieldBase(
            modifier = modifier,
            formControl = formControl,
            label = label.toString().lowercase(),
            value = AppInputDefaults.valueToString(type, formControl?.value),
            onValueChange = onValueChange,
            placeHolder = placeHolder,
            enabled = enabled,
            readOnly = readOnly,
            showOutline = showOutline,
            supportingText = supportingText,
            inputType = type,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            showTrailingIcon = showTrailingIcon,
            showTrailingIconAlways = showTrailingIconAlways,
            onTrailingAction = onTrailingAction,
            trailingIconId = trailingIconId,
            onImeAction = onImeAction,
            nextFocusRequester = nextFocusRequester,
        )
    }
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
    showOutline: Boolean = false,
    readOnly: Boolean = false,
    supportingText: String? = null,
    showTrailingIcon: Boolean = true,
    showTrailingIconAlways: Boolean = false,
    trailingIconId: Int = AppIcons.Outlined.Close,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onFocus: (() -> Unit)? = null,
    onBlur: (() -> Unit)? = null,
    onDone: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    onValueChange: ((T?) -> Unit)? = null,
    onImeAction: (() -> Unit)? = null,
    onTrailingAction: (() -> Unit)? = null,
    nextFocusRequester: FocusRequester? = null,
) {
    var isFocused by remember { mutableStateOf(false) }
    val currentOnFocus by rememberUpdatedState(onFocus)
    val currentOnBlur by rememberUpdatedState(onBlur)
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    remember { MutableInteractionSource() }

    val isError = formControl?.error?.type != null && (formControl.dirty || formControl.touched)
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isPassword = inputType == AppInputType.PASSWORD
    val showPasswordToggle = isPassword && showTrailingIcon
    val showTrailingButton = showTrailingIcon && !isPassword &&
            enabled &&
            !readOnly &&
            (showTrailingIconAlways || formControl?.value?.toString()?.isNotEmpty() == true)

    val inputTextColor =
        when {
            !enabled -> colorScheme.textSubheading
            else -> colorScheme.textBody
        }

    val inputValue = value

    val onInputChange: (String) -> Unit = { newValue ->
        if (onValueChange != null) {
            onValueChange(newValue as T?)
        } else {
            val filtered = AppInputDefaults.filterValue(inputType, newValue)
            val convertedValue =
                AppInputDefaults.stringToValue(inputType, filtered, formControl) as T?
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

    val clearIconColor = if (isError) AppIconType.Danger else AppIconType.Primary
    val trailingIcon: (@Composable (() -> Unit))? =
        when {
            showPasswordToggle -> {
                @Composable {
                    val iconResId =
                        if (passwordVisible) AppIcons.Default.EyeClosed else AppIcons.Default.EyeOpened
                    val contentDescription =
                        if (passwordVisible) "Hide password" else "Show password"
                    AppIcon(
                        id = iconResId,
                        contentDescription = contentDescription,
                        type = AppIconType.Primary, // Always use primary color for eye icon
                        onClick = { passwordVisible = !passwordVisible },
                    )
                }
            }

            showTrailingButton -> {
                @Composable {
                    AppIcon(
                        trailingIconId,
                        contentDescription = "Clear",
                        type = clearIconColor, // Use error color for clear icon when in error state
                        onClick = { onTrailingAction?.invoke() ?: clearValueAndNotify() },
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
        modifier =
            modifier
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
                }.then(
                    if (showOutline) {
                        Modifier.border(
                            width = 1.dp,
                            color = if (isError) colorScheme.textError else colorScheme.utility,
                            shape = RoundedCornerShape(size = borderRadius.sm),
                        )
                    } else {
                        Modifier
                    },
                ),
        label = {
            label?.let {
                Text(
                    text = label.lowercase(),
                    style = typography.body3,
                    color = if (isError) colorScheme.textError else colorScheme.textSubheading,
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
        textStyle = typography.body2,
        singleLine = true,
        trailingIcon = trailingIcon,
        keyboardOptions = keyboardOptions,
        keyboardActions =
            KeyboardActions(
                onDone = {
                    if (onImeAction != null) {
                        onImeAction()
                    } else if (nextFocusRequester != null) {
                        nextFocusRequester.requestFocus()
                    } else {
                        focusManager.clearFocus()
                    }
                    onDone?.invoke()
                    keyboardController?.hide()
                },
                onNext = {
                    if (onImeAction != null) {
                        onImeAction()
                    } else if (nextFocusRequester != null) {
                        nextFocusRequester.requestFocus()
                    } else {
                        focusManager.clearFocus()
                    }
                    onNext?.invoke()
                },
            ),
        enabled = enabled,
        readOnly = readOnly,
        visualTransformation = inputTransformation,
        isError = isError,
        shape = RoundedCornerShape(borderRadius.sm),
        colors =
            TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
                focusedContainerColor = colorScheme.primaryBackground,
                unfocusedContainerColor = colorScheme.primaryBackground,
                disabledContainerColor = colorScheme.secondaryActionDisabled,
                errorContainerColor = colorScheme.primaryBackground,
                focusedTextColor = inputTextColor,
                unfocusedTextColor = inputTextColor,
                disabledTextColor = colorScheme.textSubheading,
                errorTextColor = inputTextColor,
                focusedPlaceholderColor = colorScheme.secondaryActionDisabled,
                unfocusedPlaceholderColor = colorScheme.secondaryActionDisabled,
                disabledPlaceholderColor = colorScheme.secondaryActionDisabled,
                cursorColor = colorScheme.primaryAction,
                errorCursorColor = colorScheme.textError,
            ),
    )
    Box(modifier = Modifier.padding(top = spacing.none, start = spacing.sm)) {
        val errorMessage = formControl?.error?.message.orEmpty()
        when {
            isError ->
                Text(
                    text = errorMessage.lowercase(),
                    color = colorScheme.textError,
                    style = typography.body3,
                )

            supportingText != null ->
                Text(
                    text = supportingText,
                    color = colorScheme.textSubheading,
                    style = typography.body3,
                )

            else ->
                Text(
                    text = AppInputStrings.EmptySpace,
                    style = typography.body3,
                )
        }
    }
    Spacer(Modifier.height(spacing.xs))
}

@PreviewTheme
@Composable
fun AppInputPreview() {
    MeAppTheme {
        val normal = remember { FormControl.create("Input", emptyList()) }
        val disabled = remember { FormControl.create("", emptyList()) }
        val focused = remember { FormControl.create("", emptyList()) }
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            AppInput(formControl = normal, label = "Normal Input", type = AppInputType.TEXT)
            AppInput(formControl = focused, label = "Focused Input", type = AppInputType.TEXT)
            AppInput(
                formControl = disabled,
                label = "Disabled Input",
                type = AppInputType.TEXT,
                enabled = false,
                supportingText = "must not be left blank"
            )
        }
    }
}
