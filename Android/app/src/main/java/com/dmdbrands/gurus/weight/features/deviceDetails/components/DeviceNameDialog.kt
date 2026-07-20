package com.dmdbrands.gurus.weight.features.deviceDetails.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.core.shared.utilities.testing.TestTags
import com.dmdbrands.gurus.weight.features.common.components.dismissKeyboardOnTap
import com.dmdbrands.gurus.weight.features.common.components.AppInput
import com.dmdbrands.gurus.weight.features.common.components.AppInputType
import com.dmdbrands.gurus.weight.features.common.components.BaseModal
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.model.ActionButton
import com.dmdbrands.gurus.weight.features.deviceDetails.reducer.DeviceDetailsIntent
import com.dmdbrands.gurus.weight.features.deviceDetails.strings.DeviceNameDialogStrings
import com.dmdbrands.gurus.weight.features.deviceDetails.viewmodel.DeviceDetailsViewModel
import com.dmdbrands.gurus.weight.theme.MeAppTheme

/**
 * Password Reset Dialog composable using BaseModal for consistent dialog styling.
 * @param scaleName The email value to pre-fill the form.
 * @param onDismiss Called when the dialog should be dismissed.
 * @param modifier Modifier for the dialog.
 */
@Composable
fun DeviceNameModal(
  scaleId: String,
  accountId: String? = null,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val viewmodelKey = remember(scaleId, accountId) {
    "scale_${scaleId}_account_${accountId ?: "default"}"
  }
  val viewModel: DeviceDetailsViewModel =
    hiltViewModel<DeviceDetailsViewModel, DeviceDetailsViewModel.Factory>(
      key = viewmodelKey,
      creationCallback = { factory ->
        factory.create(scaleId)
      },
    )
  val state by viewModel.state.collectAsStateWithLifecycle()
  val keyboardController = LocalSoftwareKeyboardController.current
  val focusManager = LocalFocusManager.current

  // Repopulate form with current scale name when scale data changes
  LaunchedEffect(state.scale) {
    state.scale?.let { scale ->
      val scaleName = scale.nickname
      if (scaleName.isNotEmpty() && state.scaleNameForm.controls.name.value != scaleName) {
        viewModel.handleIntent(DeviceDetailsIntent.SetScaleName(scaleName))
      }
    }
  }

  BaseModal(
    title = DeviceNameDialogStrings.Title,
    primaryAction = ActionButton(
      text = DeviceNameDialogStrings.SaveButton,
      action = { viewModel.handleIntent(DeviceDetailsIntent.UpdateScaleName) },
      enabled = state.scaleNameForm.controls.name.error == null &&
        state.scaleNameForm.controls.name.value.isNotBlank(),
      testTag = TestTags.DeviceDetails.NameDialogSaveButton,
    ),
    secondaryAction = ActionButton(
      text = DeviceNameDialogStrings.CancelButton,
      action = {
        onDismiss()
      },
      testTag = TestTags.DeviceDetails.NameDialogCancelButton,
    ),
    onDismiss = {
      onDismiss()
    },
    modifier = modifier.dismissKeyboardOnTap(),
  ) {
    AppInput(
      formControl = state.scaleNameForm.controls.name,
      label = DeviceNameDialogStrings.DevicenameLabel,
      type = AppInputType.TEXT,
      testTag = TestTags.DeviceDetails.NameDialogField,
      imeAction = ImeAction.Done,
      showOutline = true,
      onImeAction = {
        viewModel.handleIntent(DeviceDetailsIntent.UpdateScaleName)
        focusManager.clearFocus()
        keyboardController?.hide()
      },
      modifier = Modifier.fillMaxWidth(),
    )
  }
}

@PreviewTheme
@Composable
fun DeviceNameModalPreview() {
  MeAppTheme {
    DeviceNameModal(
      scaleId = "1212121",
      accountId = "account123",
      onDismiss = {},
    )
  }
}
