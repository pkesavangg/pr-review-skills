package com.greatergoods.meapp.features.signup.components

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import com.greatergoods.meapp.features.common.components.AppInput
import com.greatergoods.meapp.features.common.components.AppInputType
import com.greatergoods.meapp.features.common.components.AppStyledCard
import com.greatergoods.meapp.features.common.components.AppText
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.components.TextType
import com.greatergoods.meapp.features.common.composition.LocalCardAlignment
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.features.common.helper.form.FormValidations
import com.greatergoods.meapp.features.signup.strings.SignupStrings
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme
import com.greatergoods.meapp.theme.MeTheme.colorScheme
import com.greatergoods.meapp.theme.MeTheme.spacing
import com.greatergoods.meapp.theme.MeTheme.typography

/**
 * Step for collecting user's password, confirm password, and zipcode
 */
@Composable
fun PasswordStep(
    passwordControl: FormControl<String>,
    confirmPasswordControl: FormControl<String>,
    zipcodeControl: FormControl<String>,
    onUrlOpen: (String) -> Unit,
    onSubmit: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val passwordFocusRequester = remember { FocusRequester() }
    val confirmPasswordFocusRequester = remember { FocusRequester() }
    val zipcodeFocusRequester = remember { FocusRequester() }

    AppStyledCard(
        cardAlignmentType = LocalCardAlignment.current,
    ) {
        Column {
            AppText(SignupStrings.passwordStepTitle, TextType.Title, spacing = MeTheme.spacing.xs)
            AppText(SignupStrings.passwordStepSubtitle, TextType.Subtitle, spacing = MeTheme.spacing.lg)
            AppInput(
                formControl = passwordControl,
                type = AppInputType.PASSWORD,
                label = SignupStrings.passwordLabel,
                imeAction = ImeAction.Next,
                nextFocusRequester = confirmPasswordFocusRequester,
                modifier =
                    Modifier
                        .semantics { contentType = ContentType.NewPassword }
                        .focusRequester(passwordFocusRequester),
            )
            AppInput(
                formControl = confirmPasswordControl,
                type = AppInputType.PASSWORD,
                label = SignupStrings.confirmPasswordLabel,
                imeAction = ImeAction.Next,
                nextFocusRequester = zipcodeFocusRequester,
                modifier = Modifier.focusRequester(confirmPasswordFocusRequester),
            )
            AppInput(
                formControl = zipcodeControl,
                type = AppInputType.TEXT,
                label = SignupStrings.zipcodeLabel,
                imeAction = ImeAction.Done,
                onImeAction = onSubmit,
                modifier = Modifier.focusRequester(zipcodeFocusRequester),
            )
            Spacer(modifier = Modifier.padding(bottom = MeTheme.spacing.md))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppText(SignupStrings.passwordStepFooter, TextType.Body)
                Spacer(Modifier.height(spacing.x2s))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Absolute.Center,
                ) {
                    AppText(
                        text = SignupStrings.TermsOfService,
                        textType = TextType.Link,
                        onClick = {
                            onUrlOpen(SignupStrings.TermsOfServiceUrl)
                        },
                    )
                    Spacer(Modifier.padding(start = spacing.sm))
                    Text(SignupStrings.And, style = typography.body4, color = colorScheme.textBody)
                    Spacer(Modifier.padding(end = spacing.sm))
                    AppText(
                        text = SignupStrings.PrivacyPolicy,
                        textType = TextType.Link,
                        onClick = {
                            onUrlOpen(SignupStrings.PrivacyPolicyUrl)
                        },
                    )
                }
            }
        }
    }
}

@PreviewTheme
@Composable
fun PasswordStepPreview() {
    MeAppTheme {
        PasswordStep(
            passwordControl = FormControl.create("", listOf(FormValidations.required())),
            confirmPasswordControl = FormControl.create("", listOf(FormValidations.required())),
            zipcodeControl = FormControl.create("", listOf(FormValidations.required())),
            onUrlOpen = { /* Preview only */ },
            onSubmit = {},
        )
    }
}
