package com.greatergoods.meapp.features.goal.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.domain.model.storage.Account.Account
import com.greatergoods.meapp.features.common.components.AppLinearProgressIndicator
import com.greatergoods.meapp.features.common.components.AppLinearProgressType
import com.greatergoods.meapp.features.common.components.AppStyledCard
import com.greatergoods.meapp.features.common.components.AppText
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.components.TextType
import com.greatergoods.meapp.features.common.helper.AccountHelper.convertStoredWeightToDisplay
import com.greatergoods.meapp.features.common.helper.AccountHelper.formatWeightForDisplay
import com.greatergoods.meapp.features.common.helper.AccountHelper.getWeightUnitDisplay
import com.greatergoods.meapp.features.goal.helper.GoalDisplayHelper
import com.greatergoods.meapp.features.goal.strings.GoalStrings
import com.greatergoods.meapp.features.signup.model.GoalType
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme.colorScheme
import com.greatergoods.meapp.theme.MeTheme.spacing

/**
 * Milestone display composable showing current goal progress similar to Angular milestone page.
 */
@Composable
fun GoalMilestoneDisplay(
    account: Account,
    latestWeight: Double?,
    modifier: Modifier = Modifier
) {
    // Extract values from account using AccountHelper
    val currentWeight = latestWeight ?: account.initialWeight
    val goalWeight = account.goalWeight ?: return // Don't render if no goal weight
    val initialWeight = account.initialWeight
    val goalType = when (account.goalType?.lowercase()) {
        "maintain" -> GoalType.MAINTAIN
        "lose" -> GoalType.LOSE
        "gain" -> GoalType.GAIN
        else -> GoalType.LOSE_GAIN
    }
    val goalPercent = account.goalPercent
    val weightlessWeight = account.weightlessWeight

    // Compute display progress percentage following Angular stats-modal logic
    val displayProgressPercentage = GoalDisplayHelper.computeDisplayProgressPercentage(goalType, goalPercent)

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(spacing.sm))
        // Goal Type Specific Display
        when (goalType) {
            GoalType.MAINTAIN -> {
                MaintainGoalDisplay(
                    account = account,
                    currentWeight = currentWeight,
                    goalWeight = goalWeight,
                    weightlessWeight = weightlessWeight,
                    progressPercentage = displayProgressPercentage
                )
            }
            GoalType.LOSE_GAIN,GoalType.LOSE, GoalType.GAIN -> {
                LoseGainGoalDisplay(
                    account = account,
                    currentWeight = currentWeight,
                    goalWeight = goalWeight,
                    initialWeight = initialWeight,
                    progressPercentage = displayProgressPercentage,
                    weightlessWeight = weightlessWeight
                )
            }
        }
    }
}

/**
 * Display for maintain goal - shows distance from goal weight.
 */
@Composable
private fun MaintainGoalDisplay(
    account: Account,
    currentWeight: Double,
    goalWeight: Double,
    weightlessWeight: Float?,
    progressPercentage: Double
) {
    val isWeightlessOn = account.isWeightlessOn ?: false
    val weightUnit = account.getWeightUnitDisplay()

    val displayCurrentWeight = if (isWeightlessOn && weightlessWeight != null) {
        account.convertStoredWeightToDisplay(currentWeight - weightlessWeight)
    } else {
        account.convertStoredWeightToDisplay(currentWeight)
    }
    val displayGoalWeight = account.convertStoredWeightToDisplay(goalWeight)
    val distanceText = GoalDisplayHelper.computeGoal(displayCurrentWeight, displayGoalWeight, GoalType.MAINTAIN)

    AppStyledCard (
        modifier = Modifier.background(colorScheme.primaryBackground).padding(vertical = spacing.md).clip(shape = RoundedCornerShape(24.dp))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            AppText(
                textType = TextType.Title,
                text = "$distanceText$weightUnit",
                modifier = Modifier.alignByBaseline()
            )
            AppText(
                text = "${GoalStrings.To} ${account.formatWeightForDisplay(goalWeight)} ${GoalStrings.GoalWeight}",
                textType = TextType.Body,
                modifier = Modifier.alignByBaseline()
            )
        }
    }
}

/**
 * Display for lose/gain goal - shows progress slider and remaining weight.
 * Determines if it's a lose or gain goal based on initial vs goal weight comparison.
 */
@Composable
private fun LoseGainGoalDisplay(
    account: Account,
    currentWeight: Double,
    goalWeight: Double,
    initialWeight: Double,
    progressPercentage: Double,
    weightlessWeight: Float?
) {
    val isWeightlessOn = account.isWeightlessOn ?: false
    val weightUnit = account.getWeightUnitDisplay()

    val displayCurrentWeight = if (isWeightlessOn && weightlessWeight != null) {
        account.convertStoredWeightToDisplay(currentWeight - weightlessWeight)
    } else {
        account.convertStoredWeightToDisplay(currentWeight)
    }
    val displayGoalWeight = account.convertStoredWeightToDisplay(goalWeight)
    val displayInitialWeight = account.convertStoredWeightToDisplay(initialWeight)
    val isLoseGoal = displayGoalWeight < displayInitialWeight
    val goalType = if(isLoseGoal) GoalType.LOSE else GoalType.GAIN
    val toGoal = GoalDisplayHelper.computeGoal(displayCurrentWeight, displayGoalWeight, goalType)
    AppStyledCard (
        modifier = Modifier.background(colorScheme.primaryBackground).padding(vertical = spacing.md).clip(shape = RoundedCornerShape(24.dp))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = spacing.sm)
        ) {
            AppText(
                textType = TextType.Title,
                text = if (!toGoal.startsWith("-")) "+$toGoal" else "-$toGoal", // Always show negative for lose goal
                color = colorScheme.textHeading,
                textAlign = TextAlign.Start,
                modifier = Modifier.alignByBaseline()
            )
            AppText(
                text = "$weightUnit ${GoalStrings.Goal}",
                textType = TextType.Body,
                color = colorScheme.textBody,
                textAlign = TextAlign.End,
                modifier = Modifier.alignByBaseline()
            )
        }
        //for testing it needs here don't remove this comment
        // AppText(text = (progressPercentage / 100.0).toFloat().toString(), textType = TextType.Title )

        // Progress Slider using AppLinearProgressIndicator
        AppLinearProgressIndicator(
            progress = (progressPercentage / 100.0).toFloat(),
            type = AppLinearProgressType.Primary,
            height = 8.dp,
            modifier = Modifier.fillMaxWidth().padding(start = spacing.sm,  end = spacing.sm,top = spacing.x2s, bottom = spacing.x3s)
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            AppText(
                text = account.formatWeightForDisplay(initialWeight),
                textType = TextType.Body,
                color = colorScheme.textHeading,
            )

            AppText(
                text = account.formatWeightForDisplay(goalWeight),
                textType = TextType.Body,
                color = colorScheme.textHeading,
            )
        }
    }
}

@PreviewTheme
@Composable
fun GoalMilestoneDisplayPreview() {
    MeAppTheme {
        Column {
            val previewAccount = Account(
                id = "preview-id",
                firstName = "Preview",
                lastName = "User",
                dob = "1990-01-01",
                email = "preview@example.com",
                gender = "male",
                zipcode = "12345",
                weightUnit = com.greatergoods.meapp.domain.model.common.WeightUnit.LB,
                height = 700,
                activityLevel = "normal",
                goalType = "maintain",
                goalWeight = 140.0,
                initialWeight = 160.0,
                goalPercent = 100.0,
                isWeightlessOn = false,
                weightlessWeight = null
            )

            // Maintain Goal Preview
            GoalMilestoneDisplay(
                account = previewAccount,
                latestWeight = 150.0,
                modifier = Modifier.padding(spacing.md)
            )

            Spacer(modifier = Modifier.height(spacing.lg))

            // Lose/Gain Goal Preview
            val loseGainAccount = previewAccount.copy(
                goalType = "lose",
                goalPercent = 65.0
            )
            GoalMilestoneDisplay(
                account = loseGainAccount,
                latestWeight = 150.0,
                modifier = Modifier.padding(spacing.md)
            )
        }
    }
}
