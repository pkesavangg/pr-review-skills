package com.greatergoods.meapp.features.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.features.login.strings.LoginStrings
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme.colorScheme
import com.greatergoods.meapp.theme.MeTheme.spacing
import com.greatergoods.meapp.theme.MeTheme.typography

@Composable
fun LoginScreen() {
    val loginViewModel: LoginViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()
    val emailControl = remember { FormControl("", emptyList(), emptyList(), scope) }
    val passwordControl = remember { FormControl("", emptyList(), emptyList(), scope) }
    val isFormFilled = emailControl.value.isNotBlank() && passwordControl.value.isNotBlank()

    AppScaffold(
        title = null,
        navigationIcon = {
            AppIconButton(AppIcons.Default.Close) { }
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
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                AppText(
                    text = LoginStrings.WelcomeBack,
                    textType = TextType.Title,
                )
                Spacer(Modifier.height(spacing.md))
                AppInput(
                    formControl = emailControl,
                    label = LoginStrings.EmailLabel,
                    type = AppInputType.TEXT,
                    modifier = Modifier.fillMaxWidth(),
                    showTrailingIcon = true,
                )
                AppInput(
                    formControl = passwordControl,
                    label = LoginStrings.PasswordLabel,
                    type = AppInputType.PASSWORD,
                    modifier = Modifier.fillMaxWidth(),
                    showTrailingIcon = true,
                )
                Spacer(Modifier.height(spacing.lg))
                AppButton(
                    label = LoginStrings.LoginButton,
                    enabled = isFormFilled,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    onClick = {
                        // TODO: Integrate Login
                    },
                )
                Spacer(Modifier.height(spacing.sm))
                AppButton(
                    label = LoginStrings.ForgotPassword,
                    type = ButtonType.TextPrimary,
                    size = ButtonSize.Medium,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    onClick = { },
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Absolute.Center,
                ) {
                    AppText(
                        text = LoginStrings.TermsOfService,
                        textType = TextType.Link,
                        onClick = { loginViewModel.openUrl(LoginStrings.TermsOfServiceUrl) },
                    )
                    Spacer(Modifier.padding(start = spacing.sm))
                    Text(LoginStrings.And, style = typography.body4, color = colorScheme.textBody)
                    Spacer(Modifier.padding(end = spacing.sm))
                    AppText(
                        text = LoginStrings.PrivacyPolicy,
                        textType = TextType.Link,
                        onClick = { loginViewModel.openUrl(LoginStrings.PrivacyPolicyUrl) },
                    )
                }
            }
            Spacer(Modifier.height(spacing.lg))
        }
    }
}

@PreviewTheme
@Composable
fun LoginScreenPreview() {
    MeAppTheme {
        LoginScreen()
    }
}
