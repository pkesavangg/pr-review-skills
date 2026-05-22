package com.dmdbrands.gurus.weight.features.signup.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dmdbrands.gurus.weight.features.common.components.AppHeightInput
import com.dmdbrands.gurus.weight.features.common.components.AppStyledCard
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.HeightInput
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonData
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonGroup
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonSize
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonType
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.common.composition.LocalCardAlignment
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.signup.strings.SignupStrings
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

private const val UNIT_FT_IN_ID = 0
private const val UNIT_CM_ID = 1

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
    val unitOptions = listOf(
        SegmentButtonData(id = UNIT_FT_IN_ID, label = SignupStrings.heightUnitFtIn),
        SegmentButtonData(id = UNIT_CM_ID, label = SignupStrings.heightUnitCm),
    )
    val selectedOption = if (useMetricControl.value) unitOptions[1] else unitOptions[0]

    AppStyledCard(
        cardAlignmentType = LocalCardAlignment.current,
    ) {
        AppText(SignupStrings.heightStepTitle, TextType.Title, spacing = MeTheme.spacing.xs)
        AppText(SignupStrings.heightStepSubtitle, TextType.Subtitle, spacing = MeTheme.spacing.lg)
        AppText(
            text = SignupStrings.heightLabelDynamic.format(
                if (useMetricControl.value) SignupStrings.heightUnitCm.lowercase() else "in",
            ),
            textType = TextType.Subtitle,
            spacing = MeTheme.spacing.xs,
        )
        AppHeightInput(
            formControl = heightControl,
            label = SignupStrings.heightLabel,
        )

        Spacer(modifier = Modifier.height(MeTheme.spacing.md))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            SegmentButtonGroup(
                data = unitOptions,
                selectedData = selectedOption,
                key = SegmentButtonData::label,
                onSelected = { option ->
                    val newMetric = option.id == UNIT_CM_ID
                    useMetricControl.onValueChange(newMetric)
                    onMetricToggle(newMetric)
                },
                size = SegmentButtonSize.Small,
                type = SegmentButtonType.Scrollable,
                spacedBy = MeTheme.spacing.xs,
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
            onMetricToggle = {},
        )
    }
}
