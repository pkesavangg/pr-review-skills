package com.greatergoods.meapp.features.signup.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.greatergoods.meapp.features.common.components.AppInput
import com.greatergoods.meapp.features.common.components.AppInputType
import com.greatergoods.meapp.features.common.components.AppStyledCard
import com.greatergoods.meapp.features.common.components.AppText
import com.greatergoods.meapp.features.common.components.CardAlignmentType
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.components.TextType
import com.greatergoods.meapp.features.common.composition.LocalCardAlignment
import com.greatergoods.meapp.features.signup.model.SignupData
import com.greatergoods.meapp.features.signup.strings.SignupStrings
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

/**
 * Step for collecting user's email address
 */
@Composable
fun EmailStep(
    signupData: SignupData,
    onEmailChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
        AppStyledCard(
            cardAlignmentType = LocalCardAlignment.current
        ) {
            Column {
                AppText(SignupStrings.emailStepTitle, TextType.Title, spacing = MeTheme.spacing.xs)
                AppText(SignupStrings.emailStepSubtitle, TextType.Subtitle,spacing = MeTheme.spacing.md)
                AppInput<String>(
                    formControl = null,
                    type = AppInputType.TEXT,
                    label = SignupStrings.emailLabel,
                    onValueChange = { onEmailChange(it ?: "") }
                )
            }
        }
    }

@PreviewTheme
@Composable
fun EmailStepPreview() {
    MeAppTheme {
        EmailStep(
            signupData = SignupData(email = "user@example.com"),
            onEmailChange = {}
        )
    }
}
