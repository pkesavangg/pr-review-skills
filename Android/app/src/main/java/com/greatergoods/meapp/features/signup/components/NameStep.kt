package com.greatergoods.meapp.features.signup.components

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
 * Step for collecting user's first and last name
 */
@Composable
fun NameStep(
    signupData: SignupData,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
        AppStyledCard(
            cardAlignmentType = LocalCardAlignment.current
        ) {
                AppText(SignupStrings.nameStepTitle, TextType.Title, spacing = MeTheme.spacing.xs)
                AppText(SignupStrings.nameStepSubtitle, TextType.Subtitle,spacing = MeTheme.spacing.md)
                AppInput<String>(
                    formControl = null,
                    type = AppInputType.TEXT,
                    label = SignupStrings.firstNameLabel,
                    onValueChange = { onFirstNameChange(it ?: "") }
                )
                AppInput<String>(
                    formControl = null,
                    type = AppInputType.TEXT,
                    label = SignupStrings.lastNameLabel,
                    onValueChange = { onLastNameChange(it ?: "") }
                )
            }
    }

@PreviewTheme
@Composable
fun NameStepPreview() {
    MeAppTheme {
        NameStep(
            signupData = SignupData(),
            onFirstNameChange = {},
            onLastNameChange = {}
        )
    }
}
