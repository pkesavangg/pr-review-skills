package com.greatergoods.meapp.features.login

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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.navigation.LocalNavBackStack
import com.greatergoods.meapp.features.common.components.AppButton
import com.greatergoods.meapp.features.common.components.AppIconButton
import com.greatergoods.meapp.features.common.components.AppInput
import com.greatergoods.meapp.features.common.components.AppInputType
import com.greatergoods.meapp.features.common.components.AppScaffold
import com.greatergoods.meapp.features.common.components.AppText
import com.greatergoods.meapp.features.common.components.ButtonSize
import com.greatergoods.meapp.features.common.components.ButtonType
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.components.TextType
import com.greatergoods.meapp.features.login.strings.LoginStrings
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme.colorScheme
import com.greatergoods.meapp.theme.MeTheme.spacing
import com.greatergoods.meapp.theme.MeTheme.typography
import android.util.Log

@Composable
fun LoginScreen() {
    val viewModel: LoginViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    val backStack = LocalNavBackStack.current
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }
    AppScaffold(
        title = null,
        navigationIcon = {
            AppIconButton(AppIcons.Default.Close) { backStack.removeLast() }
        },
        actions = {
            AppIconButton(AppIcons.Outlined.Help) { }
        },
        containerColor = colorScheme.secondaryBackground,
    ) { scaffoldModifier ->
        Column(
            modifier = scaffoldModifier.padding(horizontal = spacing.sm, vertical = 0.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            Spacer(Modifier.height(spacing.md))
            Column(
                modifier = Modifier
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
                )
                AppInput(
                    formControl = state.form.controls.password,
                    label = LoginStrings.PasswordLabel,
                    type = AppInputType.PASSWORD,
                    showTrailingIcon = true,
                )
                Spacer(Modifier.height(spacing.xs))
                AppButton(
                    label = LoginStrings.LoginButton,
                    enabled = viewModel.isFormValid,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    onClick = { viewModel.onSubmit() },
                )
                Spacer(Modifier.height(spacing.sm))
                AppButton(
                    label = LoginStrings.ForgotPassword,
                    type = ButtonType.TextPrimary,
                    size = ButtonSize.Medium,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    onClick = {
                        backStack.addRoute(
                            AppRoute.Home,
                        )
                    },
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
                        onClick = { viewModel.openUrl(LoginStrings.TermsOfServiceUrl) },
                    )
                    Spacer(Modifier.padding(start = spacing.sm))
                    Text(LoginStrings.And, style = typography.body4, color = colorScheme.textBody)
                    Spacer(Modifier.padding(end = spacing.sm))
                    AppText(
                        text = LoginStrings.PrivacyPolicy,
                        textType = TextType.Link,
                        onClick = { viewModel.openUrl(LoginStrings.PrivacyPolicyUrl) },
                    )
                }
            }
            Spacer(Modifier.height(spacing.lg))
        }
    }
}

@PreviewTheme()
@Composable
fun LoginScreenPreview() {
    MeAppTheme {
        LoginScreen()
    }
}
