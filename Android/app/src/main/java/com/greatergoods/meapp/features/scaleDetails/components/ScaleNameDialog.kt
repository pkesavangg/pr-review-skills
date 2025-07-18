package com.greatergoods.meapp.features.scaleDetails.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.features.common.components.AppInput
import com.greatergoods.meapp.features.common.components.AppInputType
import com.greatergoods.meapp.features.common.components.BaseModal
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.model.ActionButton
import com.greatergoods.meapp.features.scaleDetails.reducer.ScaleDetailsIntent
import com.greatergoods.meapp.features.scaleDetails.strings.ScaleNameDialogStrings
import com.greatergoods.meapp.features.scaleDetails.viewmodel.ScaleDetailsViewModel
import com.greatergoods.meapp.theme.MeAppTheme

/**
 * Password Reset Dialog composable using BaseModal for consistent dialog styling.
 * @param scaleName The email value to pre-fill the form.
 * @param onDismiss Called when the dialog should be dismissed.
 * @param modifier Modifier for the dialog.
 */
@Composable
fun ScaleNameModal(
  scaleId: String,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val viewModel: ScaleDetailsViewModel =
    hiltViewModel<ScaleDetailsViewModel, ScaleDetailsViewModel.Factory>(
      creationCallback = { factory ->
        factory.create(scaleId)
      },
    )
  val state by viewModel.state.collectAsState()
  val keyboardController = LocalSoftwareKeyboardController.current
  val focusManager = LocalFocusManager.current
  val interactionSource = remember { MutableInteractionSource() }
  // Set email when the modal is first shown

  BaseModal(
    title = ScaleNameDialogStrings.Title,
    primaryAction = ActionButton(
      text = ScaleNameDialogStrings.SaveButton,
      action = { viewModel.handleIntent(ScaleDetailsIntent.UpdateScaleName) },
      enabled = state.scaleNameForm.controls.name.error == null &&
        state.scaleNameForm.controls.name.value.isNotBlank(),
    ),
    secondaryAction = ActionButton(
      text = ScaleNameDialogStrings.CancelButton,
      action = {
        state.scaleNameForm.resetForm()
        onDismiss()
      },
    ),
    onDismiss = {
      state.scaleNameForm.resetForm()
      onDismiss()
    },
    modifier = modifier.clickable(
      interactionSource = interactionSource,
      indication = null,
      onClick = { focusManager.clearFocus() },
    ),
  ) {
    AppInput(
      formControl = state.scaleNameForm.controls.name,
      label = ScaleNameDialogStrings.ScalenameLabel,
      type = AppInputType.TEXT,
      imeAction = ImeAction.Done,
      showOutline = true,
      onImeAction = {
        viewModel.handleIntent(ScaleDetailsIntent.UpdateScaleName)
        focusManager.clearFocus()
        keyboardController?.hide()
      },
      modifier = Modifier,
    )
  }
}

@PreviewTheme
@Composable
fun ScaleNameModalPreview() {
  MeAppTheme {
    ScaleNameModal(
      scaleId = "1212121",
      onDismiss = {},
    )
  }
}
