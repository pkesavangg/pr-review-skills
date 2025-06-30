package com.greatergoods.meapp.features.signup.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.greatergoods.meapp.features.common.components.AppHeightInput
import com.greatergoods.meapp.features.common.components.AppStyledCard
import com.greatergoods.meapp.features.common.components.AppText
import com.greatergoods.meapp.features.common.components.HeightInput
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.components.TextType
import com.greatergoods.meapp.features.common.composition.LocalCardAlignment
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.features.signup.strings.SignupStrings
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

/**
 * Step for collecting user's height
 */
@Composable
fun HeightStep(
    heightControl: FormControl<HeightInput>,
    modifier: Modifier = Modifier
) {
    AppStyledCard(
        cardAlignmentType = LocalCardAlignment.current
    ) {
        AppText(SignupStrings.heightStepTitle, TextType.Title, spacing = MeTheme.spacing.xs)
        AppText(SignupStrings.heightStepSubtitle, TextType.Subtitle, spacing = MeTheme.spacing.lg)
        AppHeightInput(
            formControl = heightControl,
            label = SignupStrings.heightLabel,
        )
    }
}

@PreviewTheme
@Composable
fun HeightStepPreview() {
    MeAppTheme {
        HeightStep(
            heightControl = FormControl.create(
                HeightInput.FtIn(7, 1),
                emptyList(),
            ),
        )
    }
}
