package com.greatergoods.meapp.features.changePassword

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.features.changePassword.model.ChangePasswordFormControls
import com.greatergoods.meapp.features.changePassword.model.ChangePasswordIntent
import com.greatergoods.meapp.features.changePassword.model.ChangePasswordState
import com.greatergoods.meapp.features.changePassword.strings.ChangePasswordStrings
import com.greatergoods.meapp.features.changePassword.viewmodel.ChangePasswordViewModel
import com.greatergoods.meapp.features.common.components.AppButton
import com.greatergoods.meapp.features.common.components.AppIconButton
import com.greatergoods.meapp.features.common.components.AppInput
import com.greatergoods.meapp.features.common.components.AppInputType
import com.greatergoods.meapp.features.common.components.AppScaffold
import com.greatergoods.meapp.features.common.components.AppStyledCard
import com.greatergoods.meapp.features.common.components.ButtonSize
import com.greatergoods.meapp.features.common.components.ButtonType
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.features.common.helper.form.FormGroup
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme.colorScheme
import com.greatergoods.meapp.theme.MeTheme.spacing

/**
 * Change Password screen composable. Displays the change password form, handles user input, and shows loading/error states.
 */
@Composable
fun ChangePasswordScreen() {
    val viewmodel: ChangePasswordViewModel = hiltViewModel()
    val state by viewmodel.state.collectAsState()

    ChangePasswordContent(state, viewmodel::handleIntent)
}

@Composable
private fun ChangePasswordContent(
    state: ChangePasswordState,
    handleIntent: (ChangePasswordIntent) -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }
    val currentPasswordFocusRequester = remember { FocusRequester() }
    val newPasswordFocusRequester = remember { FocusRequester() }
    val confirmPasswordFocusRequester = remember { FocusRequester() }
    BackHandler {
        handleIntent.invoke(ChangePasswordIntent.OnRequestBack)
    }
    AppScaffold(
        title = ChangePasswordStrings.Title,
        navigationIcon = {
            AppIconButton(AppIcons.Default.Close) { handleIntent.invoke(ChangePasswordIntent.OnRequestBack) }
        },
        actions = {
            AppButton(
                ChangePasswordStrings.SaveButton,
                type = ButtonType.InlineTextPrimary,
                size = ButtonSize.Small,
                enabled = state.form.isValid && state.form.isDirty,
            ) {
                handleIntent.invoke(ChangePasswordIntent.Submit)
            }
        },
        containerColor = colorScheme.secondaryBackground,
        appBarColor = colorScheme.secondaryBackground,
    ) { scaffoldModifier ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            AppStyledCard {
                Spacer(Modifier.height(spacing.md))
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = { focusManager.clearFocus() },
                            ),
                    horizontalAlignment = Alignment.Start,
                ) {
                    AppInput(
                        formControl = state.form.controls.currentPassword,
                        label = ChangePasswordStrings.CurrentPasswordLabel,
                        type = AppInputType.PASSWORD,
                        showTrailingIcon = true,
                        imeAction = ImeAction.Next,
                        nextFocusRequester = newPasswordFocusRequester,
                        modifier =
                            Modifier
                                .semantics { contentType = ContentType.Password }
                                .focusRequester(currentPasswordFocusRequester),
                    )
                    AppInput(
                        formControl = state.form.controls.newPassword,
                        label = ChangePasswordStrings.NewPasswordLabel,
                        type = AppInputType.PASSWORD,
                        showTrailingIcon = true,
                        imeAction = ImeAction.Next,
                        nextFocusRequester = confirmPasswordFocusRequester,
                        modifier =
                            Modifier
                                .semantics { contentType = ContentType.NewPassword }
                                .focusRequester(newPasswordFocusRequester),
                    )
                    AppInput(
                        formControl = state.form.controls.confirmPassword,
                        label = ChangePasswordStrings.ConfirmPasswordLabel,
                        type = AppInputType.PASSWORD,
                        showTrailingIcon = true,
                        imeAction = ImeAction.Done,
                        onImeAction = {
                            handleIntent(ChangePasswordIntent.Submit)
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        },
                        modifier =
                            Modifier
                                .semantics { contentType = ContentType.NewPassword }
                                .focusRequester(confirmPasswordFocusRequester),
                    )
                    Spacer(Modifier.height(spacing.sm))
                    AppButton(
                        type = ButtonType.TextPrimary,
                        label = ChangePasswordStrings.ForgotPasswordLabel,
                        enabled = !state.isLoading,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        onClick = {
                            handleIntent(ChangePasswordIntent.OpenForgotPasswordModal)
                        },
                    )
                }
                Spacer(Modifier.height(spacing.lg))
            }
        }
    }
}

@PreviewTheme()
@Composable
fun ChangePasswordScreenPreview() {
    MeAppTheme {
        val dummyChangePasswordState =
            ChangePasswordState(
                form =
                    FormGroup(
                        controls =
                            ChangePasswordFormControls(
                                currentPassword = FormControl.create(""),
                                newPassword = FormControl.create(""),
                                confirmPassword = FormControl.create(""),
                            ),
                    ),
                isLoading = false,
                error = null,
            )

        ChangePasswordContent(
            state = dummyChangePasswordState,
            handleIntent = {},
        )
    }
}
