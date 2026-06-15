package com.dmdbrands.gurus.weight.features.integration.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import com.dmdbrands.gurus.weight.features.common.components.AppInput
import com.dmdbrands.gurus.weight.features.common.components.AppInputType
import com.dmdbrands.gurus.weight.features.common.components.BaseModal
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.dismissKeyboardOnTap
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.common.model.ActionButton
import com.dmdbrands.gurus.weight.features.integration.strings.IntegrationStrings
import com.dmdbrands.gurus.weight.theme.MeAppTheme

/**
 * "Request an Integration" dialog — title, prompt, single text input, Send / Cancel.
 * Send is disabled until the user types something. Uses the shared [AppInput] so the
 * Done key submits the request (per repo convention).
 */
@Composable
fun RequestIntegrationModal(
  onSend: (String) -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val requestControl = remember { FormControl.create("") }
  val trimmed = requestControl.value.trim()
  val submit = { if (trimmed.isNotEmpty()) onSend(trimmed) }

  BaseModal(
    title = IntegrationStrings.RequestModalTitle,
    subtitle = IntegrationStrings.RequestModalSubtitle,
    primaryAction = ActionButton(
      text = IntegrationStrings.RequestModalSend,
      enabled = trimmed.isNotEmpty(),
      action = submit,
    ),
    secondaryAction = ActionButton(
      text = IntegrationStrings.RequestModalCancel,
      action = onDismiss,
    ),
    onDismiss = onDismiss,
    modifier = modifier.dismissKeyboardOnTap(),
  ) {
    AppInput(
      formControl = requestControl,
      type = AppInputType.TEXT,
      label = null,
      placeHolder = IntegrationStrings.RequestModalPlaceholder,
      showOutline = true,
      showTrailingIcon = false,
      imeAction = ImeAction.Done,
      onImeAction = submit,
    )
  }
}

@PreviewTheme
@Composable
fun RequestIntegrationModalPreview() {
  MeAppTheme {
    RequestIntegrationModal(onSend = {}, onDismiss = {})
  }
}
