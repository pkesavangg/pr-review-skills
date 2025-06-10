package com.greatergoods.meapp.features.common.components.input

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.theme.MeAppTheme

/**
 * Base input composable with full form event support and error handling.
 */
@Composable
fun <T> InputFieldBase(
    modifier: Modifier = Modifier,
    formControl: FormControl<T>? = null,
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
    isPassword: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailingIcon: (@Composable (() -> Unit))? = null,
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
    val error = formControl?.error
    val touched = formControl?.touched ?: false
    val isError = !error.isNullOrBlank() && touched
    val showClear = (formControl?.value?.toString()?.isNotEmpty() ?: statelessValue.isNotEmpty()) && !isError && enabled && !readOnly
    val inputValue = formControl?.value?.toString() ?: statelessValue

    Column(modifier = modifier) {
        TextField(
            value = inputValue,
            onValueChange = { newValue ->
                if (formControl != null) {
                    formControl.onValueChange(newValue as T)
                } else {
                    statelessValue = newValue
                    onValueChange?.invoke(newValue)
                }
            },
            modifier = Modifier
                .background(colors.primary, RoundedCornerShape(4.dp))
                .border(0.dp, Color.Transparent, RoundedCornerShape(4.dp))
                .height(56.dp)
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused && isFocused) {
                        onBlur?.invoke()
                        formControl?.onBlur()
                        isFocused = false
                    } else if (focusState.isFocused && !isFocused) {
                        onFocus?.invoke()
                        isFocused = true
                    }
                },
            label = {
                Text(
                    text = label,
                    style = typography.body3,
                    color = if (isError) colors.error else colors.subheading,
                )
            },
            placeholder = {
                Text(
                    text = placeHolder,
                    style = typography.body2,
                    color = colors.subheading.copy(alpha = 0.5f),
                )
            },
            trailingIcon = trailingIcon,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedTextColor = colors.body,
                unfocusedTextColor = colors.body,
                disabledTextColor = colors.subheading.copy(alpha = 0.5f),
                errorTextColor = colors.error,
                cursorColor = colors.primaryAction,
                errorCursorColor = colors.error,
            ),
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
            visualTransformation = visualTransformation,
            isError = isError,
        )
        // Always reserve space for one line below the field for error/supporting text
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(typography.body3.lineHeight.value.dp)
                .padding(start = labelPadding, top = 2.dp)
        ) {
            when {
                isError -> Text(
                    error ?: "",
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