package com.dmdbrands.gurus.weight.features.signup.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dmdbrands.gurus.weight.features.common.components.AppHeightInput
import com.dmdbrands.gurus.weight.features.common.components.AppStyledCard
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.HeightInput
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.common.composition.LocalCardAlignment
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.signup.strings.SignupStrings
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

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
