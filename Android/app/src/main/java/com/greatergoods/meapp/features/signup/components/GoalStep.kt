package com.greatergoods.meapp.features.signup.components

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
import com.greatergoods.meapp.features.common.components.AppInput
import com.greatergoods.meapp.features.common.components.AppInputType
import com.greatergoods.meapp.features.common.components.AppStyledCard
import com.greatergoods.meapp.features.common.components.AppText
import com.greatergoods.meapp.features.common.components.AppToggle
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.components.SegmentButtonData
import com.greatergoods.meapp.features.common.components.SegmentButtonGroup
import com.greatergoods.meapp.features.common.components.SegmentButtonSize
import com.greatergoods.meapp.features.common.components.SegmentButtonType
import com.greatergoods.meapp.features.common.components.TextType
import com.greatergoods.meapp.features.common.composition.LocalCardAlignment
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.features.common.helper.form.FormValidations
import com.greatergoods.meapp.features.signup.model.GoalType
import com.greatergoods.meapp.features.signup.strings.SignupStrings
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

/**
 * Step for collecting user's weight goals with metric/imperial toggle
 */
@Composable
fun GoalStep(
    goalTypeControl: FormControl<String>,
    currentWeightControl: FormControl<String>,
    goalWeightControl: FormControl<String>,
    useMetricControl: FormControl<Boolean>,
    onMetricToggle: (Boolean) -> Unit = {},
    onNext: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val currentWeightFocusRequester = remember { FocusRequester() }
    val goalWeightFocusRequester = remember { FocusRequester() }

    val isMetric = useMetricControl.value
    val weightUnit = if (isMetric) SignupStrings.weightUnitKg else SignupStrings.weightUnitLbs

    // Goal type options
    val goalTypeOptions =
        listOf(
            SegmentButtonData(id = 0, label = SignupStrings.goalStepMaintain),
            SegmentButtonData(id = 1, label = SignupStrings.goalStepLoseGain),
        )

    AppStyledCard(
        cardAlignmentType = LocalCardAlignment.current,
    ) {
        AppText(SignupStrings.goalStepTitle, TextType.Title, spacing = MeTheme.spacing.xs)
        AppText(SignupStrings.goalStepSubtitle, TextType.Subtitle, spacing = MeTheme.spacing.lg)
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
                    val value = if (selectedOption.id == 0) GoalType.MAINTAIN.value else GoalType.LOSE_GAIN.value
                    goalTypeControl.onValueChange(value)
                },
                size = SegmentButtonSize.Small,
                type = SegmentButtonType.Single,
                key = SegmentButtonData::label,
            )
        }
        Spacer(modifier = Modifier.padding(vertical = MeTheme.spacing.sm))

        // Weight Inputs with dynamic labels
        AppInput(
            formControl = currentWeightControl,
            type = AppInputType.BODY_COMP,
            label = SignupStrings.goalStepCurrentWeightDynamic.format(weightUnit),
            imeAction = ImeAction.Next,
            nextFocusRequester = goalWeightFocusRequester,
            modifier = Modifier.focusRequester(currentWeightFocusRequester),
            enabled = goalTypeControl.value == GoalType.LOSE_GAIN.value,
        )

        AppInput(
            formControl = goalWeightControl,
            type = AppInputType.BODY_COMP,
            label = SignupStrings.goalStepGoalWeightDynamic.format(weightUnit),
            imeAction = ImeAction.Done,
            onImeAction = onNext,
            modifier = Modifier.focusRequester(goalWeightFocusRequester),
        )
        Spacer(modifier = Modifier.padding(bottom = MeTheme.spacing.sm))
        // Metric Toggle Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppText(
                text = SignupStrings.goalStepUseMetric,
                textType = TextType.Body,
            )
            AppToggle(
                checked = isMetric,
                onCheckedChange = { newValue ->
                    useMetricControl.onValueChange(newValue)
                    onMetricToggle(newValue)
                }
            )
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
            onNext = {},
        )
    }
}
