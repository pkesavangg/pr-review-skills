package com.dmdbrands.gurus.weight.features.forgotPasswordDialog.screen

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
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
import com.dmdbrands.gurus.weight.features.common.components.dismissKeyboardOnTap
import com.dmdbrands.gurus.weight.features.common.components.AppInput
import com.dmdbrands.gurus.weight.features.common.components.AppInputType
import com.dmdbrands.gurus.weight.features.common.components.BaseModal
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.model.ActionButton
import com.dmdbrands.gurus.weight.features.forgotPasswordDialog.model.ForgotPasswordDialogIntent
import com.dmdbrands.gurus.weight.features.forgotPasswordDialog.strings.ForgotPasswordDialogStrings
import com.dmdbrands.gurus.weight.features.forgotPasswordDialog.viewmodel.ForgotPasswordDialogViewModel
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing

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
    val state by viewModel.state.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
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
                    modifier = modifier.dismissKeyboardOnTap(),
                ) {
                  Spacer(modifier = Modifier.padding(top = spacing.xs))
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
