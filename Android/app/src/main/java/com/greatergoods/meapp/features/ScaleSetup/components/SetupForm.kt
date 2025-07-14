package com.greatergoods.meapp.features.ScaleSetup.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.features.ScaleSetup.components.strings.ScaleFormStrings
import com.greatergoods.meapp.features.common.components.AnnotationPosition
import com.greatergoods.meapp.features.common.components.AppInput
import com.greatergoods.meapp.features.common.components.AppInputType
import com.greatergoods.meapp.features.common.components.AppText
import com.greatergoods.meapp.features.common.components.AppToggle
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.components.TextType
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.features.common.helper.form.FormValidations
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme.spacing

/**
 * A reusable form component for scale setup screens that supports input fields,
 * optional toggles, and supporting images.
 *
 * @param formControl The form control for managing input state and validation.
 * @param modifier Modifier to be applied to the root component.
 * @param title The main title text displayed at the top.
 * @param subtitle The subtitle text displayed below the title.
 * @param label The label for the input field.
 * @param inputType The type of input field (TEXT, PASSWORD, EMAIL, etc.).
 * @param hasToggle Whether to show a toggle switch below the input.
 * @param toggleLabel The label text for the toggle switch.
 * @param toggleChecked The current state of the toggle switch.
 * @param onToggleChanged Callback invoked when the toggle state changes.
 * @param supportingImage Resource ID for an optional supporting image.
 * @param onImeAction Callback invoked when IME action is triggered.
 */
@Composable
fun <T> SetupForm(
  formControl: FormControl<T>,
  modifier: Modifier = Modifier,
  title: String,
  subtitle: String,
  subtitleAnnotatedText: String? = null,
  label: String,
  inputType: AppInputType = AppInputType.TEXT,
  hasToggle: Boolean = false,
  toggleLabel: String? = null,
  toggleChecked: Boolean = false,
  onToggleChanged: ((Boolean) -> Unit)? = null,
  supportingImage: Int? = null,
  onImeAction: (() -> Unit)? = null,
) {
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.sm, vertical = spacing.md)
            .clickable(
              interactionSource = interactionSource,
              indication = null,
              onClick = { focusManager.clearFocus() }
            ),
    ) {
        // Title and Subtitle Section
        Column(
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            AppText(
                text = title,
                textType = TextType.ListTitle2,
            )

            AppText(
                text = subtitle,
                textType = TextType.Body,
                modifier = Modifier.padding(bottom = spacing.lg),
                annotatedText = subtitleAnnotatedText,
                annotationPosition = AnnotationPosition.End,
                spanStyle = if(subtitleAnnotatedText.isNullOrEmpty()) null else SpanStyle(fontWeight = FontWeight.Bold)
            )
        }

        // Input Field Section
        AppInput(
            formControl = formControl,
            label = label,
            type = inputType,
            imeAction = ImeAction.Done,
            onImeAction = onImeAction ?: {
                focusManager.clearFocus()
            },
            enabled = !(hasToggle && toggleChecked),
            modifier = Modifier.fillMaxWidth(),
        )

        // Toggle Section
        if (hasToggle && toggleLabel != null && onToggleChanged != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Absolute.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
              AppText(
                text = toggleLabel,
                textType = TextType.Body,
              )
              AppToggle(
                checked = toggleChecked,
                onCheckedChange = onToggleChanged,
                )
            }
        }

        // Supporting Image Section
        supportingImage?.let { imageRes ->
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(id = imageRes),
                    contentDescription = ScaleFormStrings.SupportingImageDescription,
                    modifier = Modifier.size(200.dp),
                )
            }
        }
    }
}

@PreviewTheme
@Composable
fun SetupFormPreview() {
    MeAppTheme {
        var toggleState by remember { mutableStateOf(false) }
        val passwordControl = remember {
          FormControl.create("", listOf( FormValidations.required())) }
        val userNameControl = remember { FormControl.create("Kristin", emptyList()) }

        Column(
            verticalArrangement = Arrangement.spacedBy(32.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            // Wi-Fi Password Form with Toggle
            SetupForm(
                formControl = passwordControl,
                title = ScaleFormStrings.WifiPasswordTitle,
                subtitle = ScaleFormStrings.WifiPasswordSubtitle,
                label = ScaleFormStrings.PasswordLabel,
                inputType = AppInputType.PASSWORD,
                hasToggle = true,
                toggleLabel = ScaleFormStrings.NoPasswordToggleLabel,
                toggleChecked = toggleState,
                onToggleChanged = { toggleState = it
                                  passwordControl.reset()},
            )

            Spacer(modifier = Modifier.height(24.dp))

            // User Name Form without Toggle
            SetupForm(
                formControl = userNameControl,
                title = ScaleFormStrings.UserNameTitle,
                subtitle = ScaleFormStrings.UserNameSubtitle,
                label = ScaleFormStrings.UserNameLabel,
                inputType = AppInputType.TEXT,
                hasToggle = false,
                supportingImage = AppIcons.Default.UserNameScale, // Placeholder
            )
        }
    }
}
