package com.dmdbrands.gurus.weight.features.signup.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import com.dmdbrands.gurus.weight.domain.enums.GoalType
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.features.common.components.AppInput
import com.dmdbrands.gurus.weight.features.common.components.AppInputType
import com.dmdbrands.gurus.weight.features.common.components.AppStyledCard
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.AppToggle
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonData
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonGroup
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonSize
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonType
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.common.composition.LocalCardAlignment
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.common.helper.form.FormValidations
import com.dmdbrands.gurus.weight.features.signup.strings.SignupStrings
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Step for collecting user's weight goals with metric/imperial toggle
 */
@Composable
fun GoalStep(
    title: String = SignupStrings.goalStepTitle,
    subtitle: String = SignupStrings.goalStepSubtitle,
    goalTypeControl: FormControl<String>,
    currentWeightControl: FormControl<String>,
    goalWeightControl: FormControl<String>,
    useMetricControl: FormControl<Boolean>? = null,
    onMetricToggle: (Boolean) -> Unit = {},
    onGoalTypeChange: (GoalType) -> Unit = {}, // Callback for goal type changes
    onNext: () -> Unit = {},
    showCurrentWeightForMaintain: Boolean = true, // Default true for signup process
    showMetricToggle: Boolean = true, // Default true for backward compatibility
) {
    val currentWeightFocusRequester = remember { FocusRequester() }
    val goalWeightFocusRequester = remember { FocusRequester() }

    val isMetric = useMetricControl?.value ?: false
    val weightUnit = if (isMetric) WeightUnit.KG.label else WeightUnit.LB.label

    // Goal type options
    val goalTypeOptions =
        listOf(
            SegmentButtonData(id = 0, label = SignupStrings.goalStepMaintain),
            SegmentButtonData(id = 1, label = SignupStrings.goalStepLoseGain),
        )

    AppStyledCard(
        cardAlignmentType = LocalCardAlignment.current,
    ) {
        AppText(title, TextType.Title, spacing = MeTheme.spacing.xs)
        AppText(subtitle, TextType.Subtitle, spacing = MeTheme.spacing.lg)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            SegmentButtonGroup(
                data = goalTypeOptions,
                selectedData =
                    goalTypeOptions.find {
                        if (goalTypeControl.value == GoalType.MAINTAIN.value) it.id == 0 else it.id == 1
                    } ?: goalTypeOptions[1],
                onSelected = { selectedOption ->
                    val goalType = if (selectedOption.id == 0) GoalType.MAINTAIN else GoalType.LOSE_GAIN
                    val value = goalType.value
                    goalTypeControl.onValueChange(value)
                    onGoalTypeChange(goalType) // Trigger the callback
                },
                size = SegmentButtonSize.Small,
                type = SegmentButtonType.Single,
                key = SegmentButtonData::label,
            )
        }
        Spacer(modifier = Modifier.padding(vertical = MeTheme.spacing.sm))
        val shouldShowCurrentWeight =
            if (goalTypeControl.value == GoalType.MAINTAIN.value) {
                showCurrentWeightForMaintain
            } else {
                true // Always show for lose/gain
            }

        if (shouldShowCurrentWeight) {
            AppInput(
                formControl = currentWeightControl,
                type = AppInputType.BODY_COMP,
                label = SignupStrings.goalStepCurrentWeightDynamic.format(weightUnit),
                imeAction = ImeAction.Next,
                nextFocusRequester = goalWeightFocusRequester,
                modifier = Modifier.focusRequester(currentWeightFocusRequester),
                enabled = goalTypeControl.value == GoalType.LOSE_GAIN.value,
            )
        }

        AppInput(
            formControl = goalWeightControl,
            type = AppInputType.BODY_COMP,
            label = SignupStrings.goalStepGoalWeightDynamic.format(weightUnit),
            imeAction = ImeAction.Next,
            onImeAction = onNext,
            modifier =
                if (shouldShowCurrentWeight) {
                    Modifier.focusRequester(goalWeightFocusRequester)
                } else {
                    // If current weight is hidden, goal weight becomes the first input
                    Modifier.focusRequester(currentWeightFocusRequester)
                },
        )
        Spacer(modifier = Modifier.padding(bottom = MeTheme.spacing.sm))

        // Metric Toggle Section - only show if enabled
        if (showMetricToggle) {
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
                    checked = isMetric,
                    onCheckedChange = { newValue ->
                        useMetricControl?.onValueChange(newValue)
                        onMetricToggle(newValue)
                    },
                )
            }
        }
    }
}

@PreviewTheme
@Composable
fun GoalStepPreview() {
    MeAppTheme {
        GoalStep(
            goalTypeControl = FormControl.create("losegain", listOf(FormValidations.required())),
            currentWeightControl = FormControl.create("", listOf(FormValidations.required())),
            goalWeightControl = FormControl.create("", listOf(FormValidations.required())),
            useMetricControl = FormControl.create(false, emptyList()),
            onMetricToggle = {},
            onGoalTypeChange = {},
            onNext = {},
            showMetricToggle = true, // Show toggle in preview
        )
    }
}
