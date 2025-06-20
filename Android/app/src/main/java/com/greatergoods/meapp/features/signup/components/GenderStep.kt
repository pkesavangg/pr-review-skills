package com.greatergoods.meapp.features.signup.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.greatergoods.meapp.features.common.components.AppStyledCard
import com.greatergoods.meapp.features.common.components.AppText
import com.greatergoods.meapp.features.common.components.CircularSelectButton
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.components.TextType
import com.greatergoods.meapp.features.common.composition.LocalCardAlignment
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.features.common.helper.form.FormValidations
import com.greatergoods.meapp.features.signup.model.Gender
import com.greatergoods.meapp.features.signup.strings.SignupStrings
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

/**
 * Step for collecting user's biological sex/gender
 */
@Composable
fun GenderStep(
    genderControl: FormControl<String>,
    modifier: Modifier = Modifier
) {
    AppStyledCard(
        cardAlignmentType = LocalCardAlignment.current
    ) {
        AppText(SignupStrings.genderStepTitle, TextType.Title, spacing = MeTheme.spacing.xs)
        AppText(
            SignupStrings.genderStepSubtitle,
            TextType.Subtitle,
            spacing = MeTheme.spacing.md
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            CircularSelectButton(
                text = SignupStrings.genderMale,
                isSelected = genderControl.value.equals(Gender.MALE.name, ignoreCase = true),
            ){ genderControl.onValueChange("male") }

            CircularSelectButton(
                text = SignupStrings.genderFemale,
                isSelected = genderControl.value.equals(Gender.FEMALE.name, ignoreCase = true),
            ){ genderControl.onValueChange("female") }
        }
        Spacer(modifier = Modifier.padding(bottom = MeTheme.spacing.md))
    }
}

@PreviewTheme
@Composable
fun GenderStepPreview() {
    MeAppTheme {
        GenderStep(
            genderControl = FormControl.create("", listOf(FormValidations.required())),
        )
    }
}
