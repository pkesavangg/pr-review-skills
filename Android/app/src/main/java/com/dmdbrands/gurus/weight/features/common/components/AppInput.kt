package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.core.shared.utilities.testing.TestTags
import com.dmdbrands.gurus.weight.features.common.components.AppInputDefaults.visualTransformation
import com.dmdbrands.gurus.weight.features.common.helper.getDeviceType
import com.dmdbrands.gurus.weight.features.common.helper.isPhoneLike
import com.dmdbrands.gurus.weight.features.common.helper.form.DecimalInputVisualTransformation
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.common.strings.AppInputStrings
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.borderRadius
import com.dmdbrands.gurus.weight.theme.MeTheme.colorScheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing
import com.dmdbrands.gurus.weight.theme.MeTheme.typography
import android.R.attr.inputType

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

    /**
     * Free decimal entry: digits plus a single literal '.' (e.g. baby weight oz
     * "15.9" or length "20.5"). Unlike [BODY_COMP] there's no implicit-decimal
     * visual transform — the typed string is the value. Mirrors Smart Baby.
     */
    DECIMAL_STRING,
}

object AppInputDefaults {
    /**
     * Default visual height for a single-line input. On phones at default font
     * scale this is the fixed height; tablets and large-font-scale phones treat
     * it as a minimum so the input grows instead of clipping its label/value.
     */
    val SingleLineHeight = 56.dp

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
            // Phone (not Decimal): several OEM keyboards (OnePlus/Samsung) hide the '.' key under
            // KeyboardType.Decimal, forcing whole-number-only entry. The phone pad always exposes
            // '.', and the DECIMAL_STRING filter keeps only digits + a single dot. (MOB-1223)
            AppInputType.DECIMAL_STRING -> KeyboardType.Phone
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
            AppInputType.NUMBER, AppInputType.BODY_COMP -> value.filter { it.isDigit() }
            // Digits plus at most one decimal point (the first one typed).
            AppInputType.DECIMAL_STRING -> {
                val firstDot = value.indexOf('.')
                value.filterIndexed { index, c -> c.isDigit() || (c == '.' && index == firstDot) }
            }
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
    trailingText: String? = null,
    maxLength: Int? = null,
    showCharacterCounter: Boolean = false,
    onValueChange: ((T?) -> Unit)? = null,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null,
    onTrailingAction: (() -> Unit)? = null,
    nextFocusRequester: FocusRequester? = null,
    testTag: String? = null,
) {
    val visualTransformation = AppInputDefaults.visualTransformation(type)
    val keyboardOptions =
        KeyboardOptions(
            keyboardType = AppInputDefaults.keyboardType(type),
            imeAction = imeAction,
        )
    val taggedModifier = if (testTag != null) modifier.testTag(testTag) else modifier
    CompositionLocalProvider(LocalAutofillHighlightColor provides Color.Transparent) {
        InputFieldBase(
            modifier = taggedModifier,
            testTag = testTag,
            formControl = formControl,
            label = label?.lowercase(),
            value = AppInputDefaults.valueToString(type, formControl?.value),
            onValueChange = onValueChange,
            placeHolder = placeHolder,
            enabled = enabled,
            readOnly = readOnly,
            showOutline = showOutline,
            supportingText = supportingText,
            inputType = type,
            maxLength = maxLength,
            showCharacterCounter = showCharacterCounter,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            showTrailingIcon = showTrailingIcon,
            showTrailingIconAlways = showTrailingIconAlways,
            onTrailingAction = onTrailingAction,
            trailingIconId = trailingIconId,
            trailingText = trailingText,
            onImeAction = onImeAction,
            nextFocusRequester = nextFocusRequester,
        )
    }
}

/**
 * Base input composable with full form event support and error handling.
 */
@Suppress("LongMethod")
@Composable
fun <T> InputFieldBase(
    modifier: Modifier = Modifier,
    testTag: String? = null,
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
    trailingText: String? = null,
    maxLength: Int? = null,
    showCharacterCounter: Boolean = false,
    singleLine: Boolean = true,
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
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val isError = formControl?.error?.type != null && (formControl.dirty || formControl.touched)
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isPhoneLike = getDeviceType().isPhoneLike
    val isPassword = inputType == AppInputType.PASSWORD
    val showPasswordToggle = isPassword && showTrailingIcon
    val showTrailingButton = shouldShowTrailingButton(
        showTrailingIcon, isPassword, enabled, readOnly, onTrailingAction, showTrailingIconAlways, formControl,
    )
    val clearIconColor = if (isError) AppIconType.Danger else AppIconType.Primary
    val trailingIcon = inputTrailingIcon(
        showPasswordToggle = showPasswordToggle, passwordVisible = passwordVisible,
        onTogglePassword = { passwordVisible = !passwordVisible },
        trailingText = trailingText, showTrailingButton = showTrailingButton,
        trailingIconId = trailingIconId, clearIconColor = clearIconColor,
        onClear = { onTrailingAction?.invoke() ?: clearValue(inputType, formControl, onValueChange) },
        testTag = testTag,
    )
    TextField(
        value = value,
        onValueChange = onInputChangeHandler(maxLength, inputType, formControl, onValueChange),
        modifier = modifier.inputFieldModifier(
            singleLine = singleLine, isPhoneLike = isPhoneLike,
            focusRequester = focusRequester, showOutline = showOutline,
            isError = isError, errorMessage = formControl?.error?.message.orEmpty(),
            onFocus = onFocus, onBlur = onBlur, formControl = formControl,
        ),
        label = { InputFieldLabel(label, isError) },
        placeholder = { InputFieldPlaceholder(placeHolder) },
        textStyle = typography.body2,
        singleLine = singleLine,
        trailingIcon = trailingIcon,
        keyboardOptions = keyboardOptions,
        keyboardActions = inputKeyboardActions(
            onImeAction = onImeAction, nextFocusRequester = nextFocusRequester,
            focusManager = focusManager, keyboardController = keyboardController,
            onDone = onDone, onNext = onNext,
        ),
        enabled = enabled,
        readOnly = readOnly,
        visualTransformation = resolveVisualTransformation(inputType, passwordVisible, visualTransformation),
        isError = isError,
        shape = RoundedCornerShape(borderRadius.sm),
        colors = inputFieldColors(inputTextColor(enabled)),
    )
    InputSupportingRow(
        formControl = formControl, isError = isError, supportingText = supportingText,
        showCharacterCounter = showCharacterCounter, maxLength = maxLength, value = value,
    )
    Spacer(Modifier.height(spacing.xs))
}

/**
 * Resolves the text color for the input value based on the enabled state.
 */
@Composable
private fun inputTextColor(enabled: Boolean): Color =
    when {
        !enabled -> colorScheme.textSubheading
        else -> colorScheme.textBody
    }

/**
 * When a password field has its value revealed, disable the masking transform.
 */
private fun resolveVisualTransformation(
    inputType: AppInputType,
    passwordVisible: Boolean,
    visualTransformation: VisualTransformation,
): VisualTransformation =
    if (inputType == AppInputType.PASSWORD && passwordVisible) {
        VisualTransformation.None
    } else {
        visualTransformation
    }

/**
 * Builds the TextField's onValueChange handler, applying the maxLength constraint,
 * filtering, and value conversion / form-control notification.
 */
private fun <T> onInputChangeHandler(
    maxLength: Int?,
    inputType: AppInputType,
    formControl: FormControl<T>?,
    onValueChange: ((T?) -> Unit)?,
): (String) -> Unit = { newValue ->
    // Check maxLength constraint before processing the value change
    if (maxLength == null || newValue.length <= maxLength) {
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
}

/**
 * Clears the field value and notifies both the form control and value-change callback.
 */
private fun <T> clearValue(
    inputType: AppInputType,
    formControl: FormControl<T>?,
    onValueChange: ((T?) -> Unit)?,
) {
    val newValue = ""
    val clearedValue = AppInputDefaults.stringToValue(inputType, newValue, formControl) as T?
    if (clearedValue != null) {
        formControl?.onValueChange(clearedValue)
        onValueChange?.invoke(clearedValue)
    }
}

private fun <T> shouldShowTrailingButton(
    showTrailingIcon: Boolean,
    isPassword: Boolean,
    enabled: Boolean,
    readOnly: Boolean,
    onTrailingAction: (() -> Unit)?,
    showTrailingIconAlways: Boolean,
    formControl: FormControl<T>?,
): Boolean =
    showTrailingIcon && !isPassword &&
        enabled &&
        // A read-only field still shows its trailing icon when it drives a custom action
        // (e.g. a dropdown/caret via onTrailingAction); only the bare clear-X is hidden when read-only.
        (!readOnly || onTrailingAction != null) &&
        (showTrailingIconAlways || formControl?.value?.toString()?.isNotEmpty() == true)

/**
 * Builds the trailing-icon slot: password visibility toggle, trailing text, or clear
 * button (each carrying its derived testTag), or none.
 */
private fun inputTrailingIcon(
    showPasswordToggle: Boolean,
    passwordVisible: Boolean,
    onTogglePassword: () -> Unit,
    trailingText: String?,
    showTrailingButton: Boolean,
    trailingIconId: Int,
    clearIconColor: AppIconType,
    onClear: () -> Unit,
    testTag: String?,
): (@Composable (() -> Unit))? =
    when {
        showPasswordToggle -> {
            @Composable {
                val iconResId =
                    if (passwordVisible) AppIcons.Default.EyeClosed else AppIcons.Default.EyeOpened
                val contentDescription =
                    if (passwordVisible) AppInputStrings.accHidePasswordLabel else AppInputStrings.accShowPasswordLabel
                AppIcon(
                    id = iconResId,
                    contentDescription = contentDescription,
                    modifier = testTag?.let {
                        Modifier.testTag(it + TestTags.FieldSuffix.VisibilityToggle)
                    } ?: Modifier,
                    type = AppIconType.Primary, // Always use primary color for eye icon
                    onClick = onTogglePassword,
                )
            }
        }

        trailingText != null -> {
            @Composable {
                Text(
                    text = "($trailingText)",
                    style = typography.body3,
                    color = colorScheme.textSubheading,
                    modifier = Modifier.padding(end = spacing.md),
                )
            }
        }

        showTrailingButton -> {
            @Composable {
                AppIcon(
                    trailingIconId,
                    contentDescription = AppInputStrings.accClearLabel,
                    modifier = testTag?.let {
                        Modifier.testTag(it + TestTags.FieldSuffix.ClearButton)
                    } ?: Modifier,
                    type = clearIconColor, // Use error color for clear icon when in error state
                    onClick = onClear,
                )
            }
        }

        else -> null
    }

/**
 * Applies width / single-line height, focus handling (focus + blur callbacks and
 * touched-on-blur), the optional outline border, and the TalkBack error semantics.
 */
@Composable
private fun <T> Modifier.inputFieldModifier(
    singleLine: Boolean,
    isPhoneLike: Boolean,
    focusRequester: FocusRequester,
    showOutline: Boolean,
    isError: Boolean,
    errorMessage: String,
    onFocus: (() -> Unit)?,
    onBlur: (() -> Unit)?,
    formControl: FormControl<T>?,
): Modifier {
    var isFocused by remember { mutableStateOf(false) }
    val currentOnFocus by rememberUpdatedState(onFocus)
    val currentOnBlur by rememberUpdatedState(onBlur)
    return this
        .fillMaxWidth()
        // Phones / folded displays keep pixel-parity fixed height; tablets use
        // heightIn so the input can grow with the value/label (MA-3713).
        .then(
            when {
                !singleLine -> Modifier
                isPhoneLike -> Modifier.height(AppInputDefaults.SingleLineHeight)
                else -> Modifier.heightIn(min = AppInputDefaults.SingleLineHeight)
            },
        )
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
        }
        .then(
            if (showOutline) {
                Modifier.border(
                    width = 1.dp,
                    color = if (isError) colorScheme.textError else colorScheme.utility,
                    shape = RoundedCornerShape(size = borderRadius.sm),
                )
            } else {
                Modifier
            },
        )
        // TalkBack: surface the specific error message on the field node (MOB-850).
        .then(
            if (isError && errorMessage.isNotEmpty()) {
                Modifier.semantics { error(errorMessage) }
            } else {
                Modifier
            },
        )
}

@Composable
private fun InputFieldLabel(label: String?, isError: Boolean) {
    label?.let {
        Text(
            text = label.lowercase(),
            style = typography.body3,
            color = if (isError) colorScheme.textError else colorScheme.textSubheading,
        )
    }
}

@Composable
private fun InputFieldPlaceholder(placeHolder: String) {
    Text(
        text = placeHolder,
        style = typography.body2,
        color = colorScheme.secondaryActionDisabled,
    )
}

/**
 * Keyboard actions: IME action / next-focus / clear-focus routing for both the Done
 * and Next actions, preserving the onDone + keyboard-hide behaviour.
 */
private fun inputKeyboardActions(
    onImeAction: (() -> Unit)?,
    nextFocusRequester: FocusRequester?,
    focusManager: FocusManager,
    keyboardController: SoftwareKeyboardController?,
    onDone: (() -> Unit)?,
    onNext: (() -> Unit)?,
): KeyboardActions =
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
    )

@Composable
private fun inputFieldColors(inputTextColor: Color) =
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
    )

/**
 * Helper / error / warning text row plus the optional live character counter.
 */
@Composable
private fun <T> InputSupportingRow(
    formControl: FormControl<T>?,
    isError: Boolean,
    supportingText: String?,
    showCharacterCounter: Boolean,
    maxLength: Int?,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = spacing.none, start = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InputMessageText(formControl, isError, supportingText)
        // Live character counter (e.g. "0/280" → "117/280" → "280/280").
        if (showCharacterCounter && maxLength != null) {
            InputCharacterCounter(value, maxLength)
        }
    }
}

@Composable
private fun <T> RowScope.InputMessageText(
    formControl: FormControl<T>?,
    isError: Boolean,
    supportingText: String?,
) {
    val errorMessage = formControl?.error?.message.orEmpty()
    // Advisory warning (non-blocking): shown only when there's no blocking error.
    val isWarning = formControl?.warning != null && (formControl.dirty || formControl.touched)
    val warningMessage = formControl?.warning?.message.orEmpty()
    Box(modifier = Modifier.weight(1f)) {
        when {
            isError ->
                Text(
                    text = errorMessage.lowercase(),
                    color = colorScheme.textError,
                    style = typography.body3,
                )

            isWarning ->
                Text(
                    text = warningMessage.lowercase(),
                    color = colorScheme.textWarning,
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
}

@Composable
private fun InputCharacterCounter(value: String, maxLength: Int) {
    val charCount = value.length
    val counterColor =
        when {
            charCount >= maxLength -> colorScheme.textError
            charCount > 0 -> colorScheme.textBody
            else -> colorScheme.textSubheading
        }
    Text(
        text = "$charCount/$maxLength",
        color = counterColor,
        style = typography.body3,
        modifier = Modifier.padding(start = spacing.sm, end = spacing.sm),
    )
}

@PreviewTheme
@Composable
fun AppInputPreview() {
    MeAppTheme {
        val normal = remember { FormControl.create("Input", emptyList()) }
        val disabled = remember { FormControl.create("", emptyList()) }
        val focused = remember { FormControl.create("", emptyList()) }
        val maxLength = remember { FormControl.create("", emptyList()) }
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
                supportingText = "this field is required"
            )
            AppInput(
                formControl = maxLength,
                label = "Max Length Input",
                type = AppInputType.TEXT,
                maxLength = 10,
                supportingText = "Maximum 10 characters"
            )
        }
    }
}
