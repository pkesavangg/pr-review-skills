package com.greatergoods.meapp.features.signup.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.greatergoods.meapp.features.common.components.AppInput
import com.greatergoods.meapp.features.common.components.AppInputType
import com.greatergoods.meapp.features.common.components.AppStyledCard
import com.greatergoods.meapp.features.common.components.AppText
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.components.TextType
import com.greatergoods.meapp.features.common.composition.LocalCardAlignment
import com.greatergoods.meapp.features.signup.model.SignupData
import com.greatergoods.meapp.features.signup.strings.SignupStrings
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

/**
 * Step for collecting user's password, confirm password, and zipcode
 */
@Composable
fun PasswordStep(
    signupData: SignupData,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onZipcodeChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {

        AppStyledCard(
            cardAlignmentType = LocalCardAlignment.current,
        ) {
            Column {
                AppText(SignupStrings.passwordStepTitle, TextType.Title,spacing = MeTheme.spacing.xs)
                AppText(SignupStrings.passwordStepSubtitle, TextType.Subtitle, spacing = MeTheme.spacing.md)
                AppInput<String>(
                    formControl = null,
                    type = AppInputType.PASSWORD,
                    label = SignupStrings.passwordLabel,
                    onValueChange = { onPasswordChange(it ?: "") }
                )

                AppInput<String>(
                    formControl = null,
                    type = AppInputType.PASSWORD,
                    label = SignupStrings.confirmPasswordLabel,
                    onValueChange = { onConfirmPasswordChange(it ?: "") }
                )

                AppInput<String>(
                    formControl = null,
                    type = AppInputType.TEXT,
                    label = SignupStrings.zipcodeLabel,
                    onValueChange = { onZipcodeChange(it ?: "") }
                )
                Spacer(modifier = Modifier.padding(bottom = MeTheme.spacing.md))

            }
        }
    }

@PreviewTheme
@Composable
fun PasswordStepPreview() {
    MeAppTheme {
        PasswordStep(
            signupData = SignupData(),
            onPasswordChange = {},
            onConfirmPasswordChange = {},
            onZipcodeChange = {}
        )
    }
}
