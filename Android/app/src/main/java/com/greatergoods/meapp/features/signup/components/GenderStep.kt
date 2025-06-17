package com.greatergoods.meapp.features.signup.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.greatergoods.meapp.features.common.components.AppStyledCard
import com.greatergoods.meapp.features.common.components.AppText
import com.greatergoods.meapp.features.common.components.CircularSelectButton
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.components.TextType
import com.greatergoods.meapp.features.common.composition.LocalCardAlignment
import com.greatergoods.meapp.features.signup.model.Gender
import com.greatergoods.meapp.features.signup.model.SignupData
import com.greatergoods.meapp.features.signup.strings.SignupStrings
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

/**
 * Step for selecting user's gender
 */
@Composable
fun GenderStep(
    signupData: SignupData,
    onGenderChange: (Gender) -> Unit,
    modifier: Modifier = Modifier
) {
    AppStyledCard(
        cardAlignmentType = LocalCardAlignment.current,
    ) {
        AppText(SignupStrings.genderStepTitle, TextType.Title, spacing = MeTheme.spacing.xs)
        AppText(
            SignupStrings.genderStepSubtitle,
            TextType.Subtitle,
            spacing = MeTheme.spacing.md
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularSelectButton(
                text = SignupStrings.genderMale,
                isSelected = signupData.gender == Gender.MALE,
                onClick = { onGenderChange(Gender.MALE) }
            )

            CircularSelectButton(
                text = SignupStrings.genderFemale,
                isSelected = signupData.gender == Gender.FEMALE,
                onClick = { onGenderChange(Gender.FEMALE) }
            )
        }
    }
}

@PreviewTheme
@Composable
fun GenderStepPreview() {
    MeAppTheme {
        GenderStep(
            signupData = SignupData(gender = Gender.FEMALE),
            onGenderChange = {}
        )
    }
}
