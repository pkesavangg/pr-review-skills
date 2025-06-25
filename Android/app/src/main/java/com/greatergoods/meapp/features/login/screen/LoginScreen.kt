package com.greatergoods.meapp.features.login.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
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
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.navigation.LocalNavBackStack
import com.greatergoods.meapp.features.common.components.AppButton
import com.greatergoods.meapp.features.common.components.AppIconButton
import com.greatergoods.meapp.features.common.components.AppInput
import com.greatergoods.meapp.features.common.components.AppInputType
import com.greatergoods.meapp.features.common.components.AppScaffold
import com.greatergoods.meapp.features.common.components.AppStyledCard
import com.greatergoods.meapp.features.common.components.AppText
import com.greatergoods.meapp.features.common.components.ButtonSize
import com.greatergoods.meapp.features.common.components.ButtonType
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.components.TextType
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.features.common.helper.form.FormGroup
import com.greatergoods.meapp.features.common.model.DialogModel
import com.greatergoods.meapp.features.common.strings.AppPopupStrings
import com.greatergoods.meapp.features.login.model.LoginFormControls
import com.greatergoods.meapp.features.login.model.LoginIntent
import com.greatergoods.meapp.features.login.model.LoginState
import com.greatergoods.meapp.features.login.strings.LoginStrings
import com.greatergoods.meapp.features.login.viewmodel.LoginViewModel
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme.colorScheme
import com.greatergoods.meapp.theme.MeTheme.spacing
import com.greatergoods.meapp.theme.MeTheme.typography

/**
 * Login screen composable. Displays the login form, handles user input, and shows loading/error states.
 */
@Composable
fun LoginScreen() {
    val viewmodel: LoginViewModel = hiltViewModel()
    val state by viewmodel.state.collectAsState()
    val backStack = LocalNavBackStack.current
    LoginContent(state, viewmodel::handleIntent)

    if (state.form.isDirty || state.form.isTouched) {
        BackHandler {
            viewmodel.dialogQueueService.enqueue(
                DialogModel.Confirm(
                    title = AppPopupStrings.UnsavedChanges.Title,
                    message = AppPopupStrings.UnsavedChanges.Message,
                    confirmText = AppPopupStrings.UnsavedChanges.Exit,
                    cancelText = AppPopupStrings.UnsavedChanges.Return,
                    onConfirm = {
                        backStack.removeLast(AppRoute.Auth.Landing)
                        state.form.resetForm()
                    },
                ),
            )
        }
    }
}

@Composable
private fun LoginContent(state: LoginState, handleIntent: (LoginIntent) -> Unit) {
    val backStack = LocalNavBackStack.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }
    val emailFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }

    AppScaffold(
        title = null,
        navigationIcon = {
            AppIconButton(AppIcons.Default.Close) { backStack.removeLast() }
        },
        actions = {
            AppIconButton(AppIcons.Outlined.Help) { handleIntent(LoginIntent.OpenHelpModal) }
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
                    AppText(
                        text = LoginStrings.WelcomeBack,
                        textType = TextType.Title,
                    )
                    Spacer(Modifier.height(spacing.md))
                    AppInput(
                        formControl = state.form.controls.email,
                        label = LoginStrings.EmailLabel,
                        type = AppInputType.EMAIL,
                        showTrailingIcon = true,
                        imeAction = ImeAction.Next,
                        nextFocusRequester = passwordFocusRequester,
                        modifier =
                            Modifier
                                .semantics { contentType = ContentType.Username }
                                .focusRequester(emailFocusRequester),
                    )
                    AppInput(
                        formControl = state.form.controls.password,
                        label = LoginStrings.PasswordLabel,
                        type = AppInputType.PASSWORD,
                        showTrailingIcon = true,
                        imeAction = ImeAction.Done,
                        onImeAction = {
                            handleIntent(LoginIntent.Submit)
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        },
                        modifier = Modifier
                            .semantics { contentType = ContentType.Password }
                            .focusRequester(passwordFocusRequester),
                    )
                    Spacer(Modifier.height(spacing.xs))
                    AppButton(
                        label = LoginStrings.LoginButton,
                        enabled = state.form.isValid && !state.isLoading,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        onClick = {
                            keyboardController?.hide()
                            handleIntent(LoginIntent.Submit)
                        },
                    )
                    Spacer(Modifier.height(spacing.sm))
                    AppButton(
                        label = LoginStrings.ForgotPassword,
                        type = ButtonType.TextPrimary,
                        size = ButtonSize.Medium,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        onClick = { handleIntent(LoginIntent.OpenForgotPasswordModal) },
                    )
                }
                Spacer(Modifier.weight(1f))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AppText(
                        text = LoginStrings.TermsAgreement,
                        textType = TextType.Subtitle,
                    )
                    Spacer(Modifier.height(spacing.x2s))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Absolute.Center,
                    ) {
                        AppText(
                            text = LoginStrings.TermsOfService,
                            textType = TextType.Link,
                            onClick = {
                                handleIntent(LoginIntent.OpenInAppBrowser(LoginStrings.TermsOfServiceUrl))
                            },
                        )
                        Spacer(Modifier.padding(start = spacing.sm))
                        Text(LoginStrings.And, style = typography.body4, color = colorScheme.textBody)
                        Spacer(Modifier.padding(end = spacing.sm))
                        AppText(
                            text = LoginStrings.PrivacyPolicy,
                            textType = TextType.Link,
                            onClick = {
                                handleIntent(LoginIntent.OpenInAppBrowser(LoginStrings.PrivacyPolicyUrl))
                            },
                        )
                    }
                }
                Spacer(Modifier.height(spacing.lg))
            }
        }
    }
}

@PreviewTheme()
@Composable
fun LoginScreenPreview() {
    MeAppTheme {
        val dummyLoginState = LoginState(
            form = FormGroup(
                controls = LoginFormControls(
                    email = FormControl.create(""),
                    password = FormControl.create(""),
                ),
            ),
            isLoading = false,
            error = null,
        )

        LoginContent(
            state = dummyLoginState,
            handleIntent = {},
        )
    }
}
