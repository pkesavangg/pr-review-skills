package com.dmdbrands.gurus.weight.features.signup.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.common.components.AppHeightPickerModal
import com.dmdbrands.gurus.weight.features.common.components.AppInputDefaults
import com.dmdbrands.gurus.weight.features.common.components.AppStyledCard
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.HeightInput
import com.dmdbrands.gurus.weight.features.common.components.ModalConfigs
import com.dmdbrands.gurus.weight.features.common.components.ModalDialog
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonData
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonGroup
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonSize
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonType
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.common.composition.LocalCardAlignment
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.signup.strings.SignupStrings
import com.dmdbrands.gurus.weight.resources.AppIcons
import androidx.compose.material3.Icon
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

private const val UNIT_FT_IN_ID = 0
private const val UNIT_CM_ID = 1

private fun HeightInput.isEmpty(): Boolean = when (this) {
    is HeightInput.FtIn -> feet == 0 && inches == 0
    is HeightInput.Cm -> value == 0
}

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

        HeightField(heightControl = heightControl)

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

@Composable
private fun HeightField(
    heightControl: FormControl<HeightInput>,
) {
    var isModalTriggered by remember { mutableStateOf(false) }
    val value = heightControl.value
    val hasValue = !value.isEmpty()

    // Updated layout per MOB-258 / MA-4006 UX resolution:
    // Placeholder label stays on the LEFT; selected value + chevron appear on the RIGHT.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(AppInputDefaults.SingleLineHeight)
            .clip(RoundedCornerShape(MeTheme.borderRadius.sm))
            .background(MeTheme.colorScheme.primaryBackground)
            .clickable { isModalTriggered = true }
            .padding(horizontal = MeTheme.spacing.md),
    ) {
        // Left: static placeholder label — always visible
        AppText(
            text = SignupStrings.heightLabel.lowercase(),
            textType = TextType.SubHeading,
            modifier = Modifier.align(Alignment.CenterStart),
        )

        // Right: selected value (value string already contains the unit) + dropdown chevron
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MeTheme.spacing.xs),
        ) {
            if (hasValue) {
                // Figma: Mobile/Heading 5 — Bold, 16px, dark (#2c2827)
                AppText(
                    text = value.getString(),
                    textType = TextType.ListTitle1,
                )
            }
            Icon(
                painter = painterResource(AppIcons.Default.ChevronDown),
                contentDescription = null,
                tint = MeTheme.colorScheme.textSubheading,
                modifier = Modifier.size(20.dp),
            )
        }
    }

    if (isModalTriggered) {
        ModalDialog(
            onDismiss = { isModalTriggered = false },
            config = ModalConfigs.Critical,
        ) {
            AppHeightPickerModal(
                value = value,
                onCancel = { isModalTriggered = false },
                onOk = { data ->
                    isModalTriggered = false
                    heightControl.onValueChange(data)
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
                HeightInput.FtIn(feet = 7, inches = 1),
                emptyList(),
            ),
            useMetricControl = FormControl.create(false, emptyList()),
            onMetricToggle = {},
        )
    }
}
