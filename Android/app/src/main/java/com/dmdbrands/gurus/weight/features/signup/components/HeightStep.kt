package com.dmdbrands.gurus.weight.features.signup.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dmdbrands.gurus.weight.features.common.components.AppHeightInput
import com.dmdbrands.gurus.weight.features.common.components.AppStyledCard
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.AppToggle
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
    modifier: Modifier = Modifier,
    useMetricControl: FormControl<Boolean>,
    onMetricToggle: (Boolean) -> Unit,
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
      // Metric Toggle Section - only show if enabled
      Spacer(modifier = Modifier.height(MeTheme.spacing.lg))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        AppText(
          text = SignupStrings.goalStepUseMetric,
          textType = TextType.Body,
        )
        AppToggle(
          checked = useMetricControl.value,
          onCheckedChange = { newValue ->
            useMetricControl.onValueChange(newValue)
            onMetricToggle(newValue)
          },
        )
      }
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
            useMetricControl = FormControl.create(false, emptyList()),
            onMetricToggle = {}
        )
    }
}
