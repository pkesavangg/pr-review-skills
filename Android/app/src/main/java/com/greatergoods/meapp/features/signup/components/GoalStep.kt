package com.greatergoods.meapp.features.signup.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.greatergoods.meapp.features.common.components.AppInput
import com.greatergoods.meapp.features.common.components.AppInputType
import com.greatergoods.meapp.features.common.components.AppStyledCard
import com.greatergoods.meapp.features.common.components.AppText
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.components.SegmentButtonData
import com.greatergoods.meapp.features.common.components.SegmentButtonGroup
import com.greatergoods.meapp.features.common.components.SegmentButtonSize
import com.greatergoods.meapp.features.common.components.TextType
import com.greatergoods.meapp.features.common.composition.LocalCardAlignment
import com.greatergoods.meapp.features.signup.model.GoalType
import com.greatergoods.meapp.features.signup.model.SignupData
import com.greatergoods.meapp.features.signup.strings.SignupStrings
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

/**
 * Step for collecting user's weight goals
 */
@Composable
fun GoalStep(
    signupData: SignupData,
    onGoalTypeChange: (GoalType) -> Unit,
    onCurrentWeightChange: (Float?) -> Unit,
    onGoalWeightChange: (Float?) -> Unit,
    modifier: Modifier = Modifier
) {
    AppStyledCard(
        cardAlignmentType = LocalCardAlignment.current,
    ) {
            AppText(SignupStrings.goalStepTitle, TextType.Title, spacing = MeTheme.spacing.xs)
            AppText(SignupStrings.goalStepSubtitle, TextType.Subtitle,spacing = MeTheme.spacing.md)
            SegmentButtonGroup(
                data = GoalType.entries.toTypedArray().mapIndexed { index, label ->
                    SegmentButtonData(id = index, label = when(label){
                        GoalType.MAINTAIN -> SignupStrings.goalStepMaintain
                        GoalType.LOSE_GAIN -> SignupStrings.goalStepLoseGain
                    })
                },
                selectedIndex = signupData.goalType.ordinal,
                onSelected = { index -> onGoalTypeChange(GoalType.entries[index]) },
                size = SegmentButtonSize.Large,
            )

            Spacer(modifier = Modifier.height(MeTheme.spacing.md))

            // Current weight input (disabled for maintain)
            AppInput<String>(
                formControl = null,
                type = AppInputType.WEIGHT,
                label = SignupStrings.goalStepCurrentWeight,
                enabled = signupData.goalType != GoalType.MAINTAIN,
                onValueChange = { value ->
                    onCurrentWeightChange(value?.toFloatOrNull())
                }
            )

            // Goal weight input
            AppInput<String>(
                formControl = null,
                type = AppInputType.WEIGHT,
                label = SignupStrings.goalStepGoalWeight,
                onValueChange = { value ->
                    onGoalWeightChange(value?.toFloatOrNull())
                }
            )
    }
}

@PreviewTheme
@Composable
fun GoalStepPreview() {
    MeAppTheme {
        GoalStep(
            signupData = SignupData(
                goalType = GoalType.LOSE_GAIN,
                currentWeight = 150f,
                goalWeight = 140f,
                useMetric = false
            ),
            onGoalTypeChange = {},
            onCurrentWeightChange = {},
            onGoalWeightChange = {},
        )
    }
}
