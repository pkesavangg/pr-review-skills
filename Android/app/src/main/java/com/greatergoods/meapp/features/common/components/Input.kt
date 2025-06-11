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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.R
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.features.common.review.ReviewViewModel
import com.greatergoods.meapp.theme.MeAppTheme
import kotlinx.coroutines.launch
import android.app.Activity

/**
 * Enum for supported input types.
 */
enum class InputType {
    TEXT,
    EMAIL,
    PASSWORD,
    CHECKBOX,
    DATE_PICKER,
    NUMBER,
    TIME_PICKER,
    DROP_DOWN,
}

/**
 * A flexible, theme-aware input composable supporting validation, error/supporting text, and icons.
 *
 * @param modifier Modifier for styling.
 * @param formControl Optional FormControl for field state/validation. If null, use value/onValueChange.
 * @param label The visible field label (lowercase, above the input).
 * @param name The field name (for internal identification).
 * @param type The input type (TEXT, EMAIL, PASSWORD, etc.).
 * @param placeHolder Optional placeholder text.
 * @param onValueChange Callback for value changes (used if formControl is null, type: (String) -> Unit).
 * @param supportingText Optional supporting/help text.
 * @param enabled Whether the field is enabled.
 * @param readOnly Whether the field is read-only.
 * @param showTrailingIcon Whether to show trailing error/clear icons.
 * @param stringToValue Lambda to convert string to T.
 * @param valueToString Lambda to convert T to string.
 */
@Composable
fun <T> InputField(
    modifier: Modifier = Modifier,
    formControl: FormControl<T>? = null,
    label: String,
    name: String = "",
    type: InputType = InputType.TEXT,
    placeHolder: String = "",
    onValueChange: ((String) -> Unit)? = null,
    supportingText: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    showTrailingIcon: Boolean = true,
    stringToValue: (String) -> T = { it as T },
    valueToString: (T) -> String = { it?.toString() ?: "" },
) {
    val spacing = MeAppTheme.spacing
    val colors = MeAppTheme.colorScheme
    val typography = MeAppTheme.typography
    val labelPadding = spacing.xs
    val inputPaddingStart = spacing.sm
    val inputPaddingEnd = spacing.sm

    var statelessValue by remember { mutableStateOf("") }
    var statelessError by remember { mutableStateOf<String?>(null) }
    val value = formControl?.value?.let(valueToString) ?: statelessValue
    val error = formControl?.error ?: statelessError
    val touched = formControl?.touched ?: false
    val pending = formControl?.pending ?: false
    val isError = !error.isNullOrBlank() && touched
    val showClear = value.isNotEmpty() && !isError && enabled && !readOnly && showTrailingIcon
    val isDisabled = !enabled

    val labelColor =
        when {
            isError -> colors.error
            isDisabled -> colors.subheading.copy(alpha = 0.5f)
            else -> colors.subheading
        }
    val inputTextColor =
        when {
            isDisabled -> colors.subheading.copy(alpha = 0.5f)
            else -> colors.body
        }
    val placeholderColor = colors.subheading.copy(alpha = 0.5f)
    val backgroundColor =
        when {
            isDisabled -> colors.secondary.copy(alpha = 0.5f)
            else -> colors.primary
        }
    val iconTint =
        when {
            isError -> colors.error
            isDisabled -> colors.secondaryDisabled
            else -> colors.primaryAction
        }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val visualTransformation =
        if (type == InputType.PASSWORD && !passwordVisible) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        }
    val keyboardType =
        when (type) {
            InputType.EMAIL -> KeyboardType.Email
            InputType.NUMBER -> KeyboardType.Number
            InputType.PASSWORD -> KeyboardType.Password
            else -> KeyboardType.Text
        }
    val trailingIcon: (@Composable (() -> Unit))? =
        when {
            isError && showTrailingIcon -> { // Only show error icon if showTrailingIcon is true
                @Composable {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close_outlined),
                        contentDescription = "Error",
                        tint = iconTint,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            type == InputType.PASSWORD && showTrailingIcon -> { // Password visibility icon
                @Composable {
                    val iconResId =
                        if (passwordVisible) R.drawable.ic_eye_close else R.drawable.ic_eye_open // Assume you have these drawables
                    val contentDescription = if (passwordVisible) "Hide password" else "Show password"
                    Icon(
                        painter = painterResource(id = iconResId),
                        contentDescription = contentDescription,
                        tint = iconTint,
                        modifier =
                            Modifier
                                .size(20.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable(enabled = enabled) { passwordVisible = !passwordVisible },
                    )
                }
            }

            showClear -> { // Clear icon
                @Composable {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close_outlined),
                        contentDescription = "Clear",
                        tint = iconTint,
                        modifier =
                            Modifier
                                .size(20.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable(
                                    enabled = enabled,
                                    onClick = {
                                        if (formControl != null) {
                                            formControl.onValueChange(stringToValue(""))
                                        } else {
                                            statelessValue = ""
                                            onValueChange?.invoke("")
                                        }
                                    },
                                ),
                    )
                }
            }

            else -> null
        }

    var isFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    Column(modifier = modifier) {
        TextField(
            value = value,
            onValueChange = { newValue ->
                if (formControl != null) {
                    formControl.onValueChange(stringToValue(newValue))
                } else {
                    statelessValue = newValue
                    onValueChange?.invoke(newValue)
                }
            },
            modifier = Modifier
                .background(backgroundColor, RoundedCornerShape(8.dp))
                .border(0.dp, Color.Transparent, RoundedCornerShape(8.dp))
                .height(56.dp)
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    if (isFocused && !focusState.isFocused) {
                        // Lost focus (blur)
                        focusManager.clearFocus()
                        focusRequester.freeFocus()
                        formControl?.onBlur()
                    }
                    isFocused = focusState.isFocused
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
            trailingIcon = {
                trailingIcon?.invoke()
            },
            colors =
                TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    // Set text colors
                    focusedTextColor = inputTextColor,
                    unfocusedTextColor = inputTextColor,
                    disabledTextColor = inputTextColor,
                    errorTextColor = inputTextColor,
                    // Set cursor color
                    cursorColor = colors.primaryAction,
                    errorCursorColor = colors.error,
                ),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            enabled = enabled,
            readOnly = readOnly,
        )
        // Always reserve space for one line below the field for error/supporting text
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(typography.body3.lineHeight.value.dp)
                    .padding(start = labelPadding, top = 2.dp),
        ) {
            when {
                isError ->
                    Text(
                        error,
                        color = colors.error,
                        style = typography.body3,
                    )

                supportingText != null ->
                    Text(
                        supportingText,
                        color = colors.subheading,
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
fun InputFieldPreview() {
    MeAppTheme {
        val fakeScope = rememberCoroutineScope()
        val formControl =
            remember {
                FormControl<String>(
                    "test",
                    validators = listOf({ if (it.isBlank()) "Required" else null }),
                    scope = fakeScope,
                )
            }
        Column(modifier = Modifier.padding(20.dp)) {
            InputField<String>(
                formControl = formControl,
                label = "email",
                name = "email",
                type = InputType.EMAIL,
                placeHolder = "Enter your email",
                showTrailingIcon = true,
                modifier = Modifier.width(210.dp).height(56.dp),
            )
        }
    }
}

/**
 * A composable that displays a review prompt button when the app is eligible for review.
 * This component handles the UI and interaction logic for requesting app reviews.
 *
 * @param viewModel The ViewModel that manages the review state and business logic.
 *                  If not provided, it will be automatically injected using Hilt.
 */
@Composable
fun ReviewPrompt(viewModel: ReviewViewModel = hiltViewModel()) {
    // Get the current context and coroutine scope
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Observe the review eligibility state
    val showReview by viewModel.shouldShowReview.collectAsState()

    // Check review eligibility when the composable is first launched
    LaunchedEffect(Unit) {
        viewModel.checkReviewEligibility()
    }

    // Show the review button only if the app is eligible for review
    if (showReview) {
        Button(
            onClick = {
                val activity = context as? Activity
                if (activity != null) {
                    scope.launch {
                        viewModel.launchReview(activity) { success ->
                            // Optional: show message or snackbar
                        }
                    }
                }
            },
        ) {
            Text("Leave a Review")
        }
    }
}
