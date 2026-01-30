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
import androidx.compose.runtime.LaunchedEffect
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
import com.dmdbrands.gurus.weight.features.common.helper.form.ValidationError
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.borderRadius
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing
import com.dmdbrands.library.ggbluetooth.model.GGBTUser

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
 * @param enableScroll Whether to enable vertical scrolling of the form content. Defaults to true.
 *                    Set to false if the form is already inside a scrollable container to avoid nested scrolling.
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
  isCustomization: Boolean = false,
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
  enableScroll: Boolean = true, // New parameter to control scrolling
  userList: List<GGBTUser> = emptyList(), // List of existing usernames to check for duplicates
) {
  // Add duplicate name validator if userList is provided (same as Angular version)
    // Use LaunchedEffect to refresh validation whenever userList changes
  val errorMessage = if(isCustomization) BtWifiScaleSetupStrings.DuplicateUser.UserErrorMessage else BtWifiScaleSetupStrings.DuplicateUser.ErrorMessage
  LaunchedEffect(userList) {
    // Remove any existing duplicate validators first
    formControl.removeValidator("DUPLICATE_NAME")

    if (userList.isNotEmpty()) {
      // Add new duplicate validator
      formControl.addValidator { value ->
        if (value?.toString()?.let { name ->
            userList.any { user -> user.name.equals(name, ignoreCase = true) }
          } == true ) {
          ValidationError("DUPLICATE_NAME", errorMessage)
        } else null
      }
    }

    // Force validation refresh to update any existing errors
    formControl.validate()
  }
  val focusManager = LocalFocusManager.current
  val interactionSource = remember { MutableInteractionSource() }

  Column(
    modifier = modifier
      .fillMaxSize()
      .then(
        // Only apply vertical scroll if enabled
        if (enableScroll) {
          Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.sm, vertical = spacing.md)
        } else {
          Modifier
            .fillMaxSize()
            .padding(horizontal = 0.dp, vertical = 0.dp)
        },
      )
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
      modifier = Modifier.padding(top = spacing.xs,bottom = spacing.lg),
      annotatedText = subtitleAnnotatedText,
      annotationPosition = AnnotationPosition.End,
      spanStyle = if (subtitleAnnotatedText.isNullOrEmpty()) null else SpanStyle(fontWeight = FontWeight.Bold),
    )

     wifiNameFormControl?.let {
       if(!isWifiConnected){
         AppInput(
           formControl = it,
           label = secondaryLabel,
           imeAction = ImeAction.Done,
           onImeAction =  {
             focusManager.clearFocus()
           },
           enabled = true,
           modifier = Modifier.fillMaxWidth(),
         )
       }
    }

    if (isWifiConnected) {
      WifiItem(
        borderRadius = borderRadius.sm,
        ssid = wifiNameFormControl?.value.toString(),
        isConfigured = false,
        index = 0,
        total = 1,
        onClick = { onImeAction?.invoke() },
        modifier = Modifier.size(spacing.md)
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
    Column(
      verticalArrangement = Arrangement.spacedBy(32.dp),
      modifier = Modifier.padding(16.dp),
    ) {
      // Normal state
      val normalControl = remember { FormControl.create("Kristin", emptyList()) }
      SetupForm(
        formControl = normalControl,
        title = ScaleFormStrings.UserNameTitle,
        subtitle = ScaleFormStrings.UserNameSubtitle,
        label = ScaleFormStrings.UserNameLabel,
        inputType = AppInputType.TEXT,
      )

      // Duplicate check state
      val duplicateControl = remember { FormControl.create("John", emptyList()) }
      SetupForm(
        formControl = duplicateControl,
        title = "Duplicate User",
        subtitle = "Choose a new user name to proceed.",
        label = "User name",
        inputType = AppInputType.TEXT,
        supportingButtonLabel = "Restore Account",
        onSupportingButtonClick = {},
        supportText = "Last active June 10, 2019",
        userList = listOf(
          GGBTUser(
            name = "John",
            token = "token1",
            lastActive = System.currentTimeMillis(),
            isBodyMetricsEnabled = true,
          ),
          GGBTUser(
            name = "Jane",
            token = "token2",
            lastActive = System.currentTimeMillis(),
            isBodyMetricsEnabled = false,
          ),
        ), // Existing users
      )

      // WiFi setup state
      val wifiControl = remember { FormControl.create("MyNetwork", listOf(FormValidations.required())) }
      SetupForm(
        formControl = wifiControl,
        title = "WiFi Setup",
        subtitle = "Enter network details",
        label = "Password",
        inputType = AppInputType.PASSWORD,
        hasToggle = true,
        toggleLabel = BtWifiScaleSetupStrings.WifiPassword.NetworkPasswordToggleLabel,
        toggleChecked = false,
        onToggleChanged = {},
        isWifiConnected = true,
        noteMessage = "Your phone should stay connected to the chosen 2.4 GHZ network until setup is complete.",
      )
    }
  }
}
