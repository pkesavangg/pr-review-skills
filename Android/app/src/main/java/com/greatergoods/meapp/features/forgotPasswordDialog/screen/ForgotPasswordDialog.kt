package com.greatergoods.meapp.features.forgotPasswordDialog.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.greatergoods.meapp.features.forgotPasswordDialog.model.ForgotPasswordDialogIntent
import com.greatergoods.meapp.features.forgotPasswordDialog.strings.ForgotPasswordDialogStrings
import com.greatergoods.meapp.features.forgotPasswordDialog.viewmodel.ForgotPasswordDialogViewModel
import com.greatergoods.meapp.features.login.model.LoginIntent
import com.greatergoods.meapp.theme.MeAppTheme

/**
 * Password Reset Dialog composable using BaseModal for consistent dialog styling.
 * @param email The email value to pre-fill the form.
 * @param onDismiss Called when the dialog should be dismissed.
 * @param modifier Modifier for the dialog.
 */
@Composable
fun PasswordResetModal(
    email: String = "",
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ForgotPasswordDialogViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }
    // Set email when the modal is first shown
    LaunchedEffect(Unit) {
        viewModel.setInitialEmail(email)
    }

    BaseModal(
        title = ForgotPasswordDialogStrings.Title,
        body = ForgotPasswordDialogStrings.Subtitle,
        primaryAction = ActionButton(
            text = ForgotPasswordDialogStrings.SubmitButton,
            action = { viewModel.handleIntent(ForgotPasswordDialogIntent.Submit) },
            enabled = viewModel.isSubmitEnabled,
        ),
        secondaryAction = ActionButton(
            text = ForgotPasswordDialogStrings.CancelButton,
            action = {
                viewModel.handleIntent(ForgotPasswordDialogIntent.Close)
                onDismiss()
            },
        ),
        onDismiss = {
            viewModel.handleIntent(ForgotPasswordDialogIntent.Close)
            onDismiss()
        },
        modifier = modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = { focusManager.clearFocus() }
        ),
    ) {
        AppInput(
            formControl = state.form.controls.email,
            label = ForgotPasswordDialogStrings.EmailLabel,
            type = AppInputType.EMAIL,
            imeAction = ImeAction.Done,
            showOutline = true,
            onImeAction = {
                viewModel.handleIntent(ForgotPasswordDialogIntent.Submit)
                focusManager.clearFocus()
                keyboardController?.hide() },
            modifier = Modifier,
        )
    }
}

@PreviewTheme
@Composable
fun PasswordResetModalPreview() {
    MeAppTheme {
        PasswordResetModal(
            email = "",
            onDismiss = {},
        )
    }
}
