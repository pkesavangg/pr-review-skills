package com.dmdbrands.gurus.weight.features.signup.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dmdbrands.gurus.weight.domain.enums.Gender
import com.dmdbrands.gurus.weight.features.common.components.AppStyledCard
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.CircularSelectButton
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.common.composition.LocalCardAlignment
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.common.helper.form.FormValidations
import com.dmdbrands.gurus.weight.features.signup.strings.SignupStrings
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Step for collecting user's biological sex/gender
 */
@Composable
fun GenderStep(
    genderControl: FormControl<String>,
    modifier: Modifier = Modifier,
) {
    AppStyledCard(
        cardAlignmentType = LocalCardAlignment.current,
    ) {
        AppText(SignupStrings.genderStepTitle, TextType.Title, spacing = MeTheme.spacing.xs)
        AppText(
            SignupStrings.genderStepSubtitle,
            TextType.Subtitle,
            spacing = MeTheme.spacing.lg,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            CircularSelectButton(
                text = SignupStrings.genderMale,
                isSelected = genderControl.value.equals(Gender.MALE.name, ignoreCase = true),
            ) { genderControl.onValueChange(Gender.MALE.value) }

            CircularSelectButton(
                text = SignupStrings.genderFemale,
                isSelected = genderControl.value.equals(Gender.FEMALE.name, ignoreCase = true),
            ) { genderControl.onValueChange(Gender.FEMALE.value) }
        }
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
