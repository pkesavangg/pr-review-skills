package com.dmdbrands.gurus.weight.features.ScaleSetup.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.strings.ScaleFormStrings
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.BtWifiScaleSetupStrings
import com.dmdbrands.gurus.weight.features.common.components.AnnotationPosition
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.AppInput
import com.dmdbrands.gurus.weight.features.common.components.AppInputType
import com.dmdbrands.gurus.weight.features.common.components.AppNote
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.AppToggle
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.common.helper.form.FormValidations
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.borderRadius
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing

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
  wifiNameFormControl: FormControl<T>? = null,
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
  supportingButtonLabel: String? = null,
  supportText: String? = null,
  isWifiConnected: Boolean = false,
  secondaryLabel: String? = null,
  noteMessage: String? = null,
  onSupportingButtonClick: (() -> Unit)? = null,
  onImeAction: (() -> Unit)? = null,
) {
  val focusManager = LocalFocusManager.current
  val interactionSource = remember { MutableInteractionSource() }

  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = spacing.sm, vertical = spacing.md)
      .verticalScroll(rememberScrollState())
      .clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = { focusManager.clearFocus() },
      ),
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
      spanStyle = if (subtitleAnnotatedText.isNullOrEmpty()) null else SpanStyle(fontWeight = FontWeight.Bold),
    )

    wifiNameFormControl?.let {
      AppInput(
        formControl = it,
        label = secondaryLabel,
        type = inputType,
        imeAction = ImeAction.Done,
        onImeAction = onImeAction ?: {
          focusManager.clearFocus()
        },
        enabled = true,
        modifier = Modifier.fillMaxWidth(),
      )
    }

    if (isWifiConnected) {
      WifiItem(
        borderRadius = borderRadius.sm,
        ssid = "greatergoods1",
        isConfigured = false,
        index = 0,
        total = 1,
        onClick = {},

        )
      Spacer(modifier = Modifier.padding(bottom = spacing.sm))
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



    if (supportingButtonLabel != null && onSupportingButtonClick != null) {
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        AppButton(
          label = supportingButtonLabel,
          type = ButtonType.InlineTextPrimary,
          onClick = onSupportingButtonClick,
        )
        supportText?.let {
          AppText(
            text = supportText,
            textType = TextType.Body,
            modifier = Modifier.padding(bottom = spacing.lg),
          )
        }
      }
    }

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

    noteMessage?.let {
      Spacer(modifier = Modifier.padding(top = spacing.lg))
      AppNote(
        message = noteMessage,
        showNote = true,
      )
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
    remember {
      FormControl.create("SSID", listOf(FormValidations.required()))
    }
    remember {
      FormControl.create("", listOf(FormValidations.required()))
    }
    val userNameControl = remember { FormControl.create("Kristin", emptyList()) }

    Column(
      verticalArrangement = Arrangement.spacedBy(32.dp),
      modifier = Modifier.padding(16.dp),
    ) {

      // User Name Form without Toggle
      SetupForm(
        formControl = userNameControl,
        title = ScaleFormStrings.UserNameTitle,
        subtitle = ScaleFormStrings.UserNameSubtitle,
        label = ScaleFormStrings.UserNameLabel,
        inputType = AppInputType.TEXT,
        hasToggle = true,
        toggleLabel = BtWifiScaleSetupStrings.WifiPassword.NetworkPasswordToggleLabel,
        toggleChecked = true,
        onToggleChanged = {},
        // supportingImage = AppIcons.Setup.UserNameScale, // Placeholder
        // supportingButtonLabel = "Restore Account",
        // onSupportingButtonClick = {},
        // supportText = "Last active June 10, 2019",
        isWifiConnected = true,
        noteMessage = "Your phone should stay connected to the chosen 2GHZ network until setup is complete. ",
      )
    }
  }
}
