package com.dmdbrands.gurus.weight.features.goal.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.domain.enums.GoalType
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.goal.Goal
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.features.common.components.AppLinearProgressIndicator
import com.dmdbrands.gurus.weight.features.common.components.AppLinearProgressType
import com.dmdbrands.gurus.weight.features.common.components.AppStyledCard
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.common.helper.AccountHelper
import com.dmdbrands.gurus.weight.features.common.helper.AccountHelper.isMetricUnit
import com.dmdbrands.gurus.weight.features.goal.helper.GoalDisplayHelper
import com.dmdbrands.gurus.weight.features.goal.helper.Weightless
import com.dmdbrands.gurus.weight.features.goal.strings.GoalStrings
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.colorScheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing
import kotlin.math.floor

/**
 * Milestone display composable showing current goal progress similar to Angular milestone page.
 */
@Composable
fun GoalMilestoneDisplay(
  account: Account,
  latestWeight: Double?,
  modifier: Modifier = Modifier,
) {
  val isMetric = account.isMetricUnit()
  val targetUnit = if (isMetric) WeightUnit.KG else WeightUnit.LB
  // Extract values from account using AccountHelper
  val currentWeight = latestWeight ?: 0.0
  val goalWeight = account.goalWeight ?: return // Don't render if no goal weight
  val initialWeight = account.initialWeight
  val goalType =
    when (account.goalType?.lowercase()) {
      "maintain" -> GoalType.MAINTAIN
      "lose" -> GoalType.LOSE
      "gain" -> GoalType.GAIN
      else -> GoalType.LOSE_GAIN
    }

  // Get current weights and create a Goal object
  val goal = Goal(
    goalWeight = goalWeight,
    initialWeight = initialWeight,
    type = goalType.value,
  ).process(targetUnit, null)
  val weightlessWeight = account.weightlessWeight
  // we are sending goal weight and initial weight from account which is coming as stored weight
  // Calculate goal percentage using the same logic as GoalViewModel and GoalService
  val goalPercent =
  calculateGoalPercentage(
    goalType = goalType,
    initialWeight = goal.initialWeight,
    goalWeight = goal.goalWeight,
    latestWeight = AccountHelper.processStoredWeightToDisplay(currentWeight * 10, account.weightUnit) * 10,
  )
  val displayProgressPercentage = GoalDisplayHelper.computeDisplayProgressPercentage(goalType, goalPercent)
  Column(
    modifier = modifier
      .fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    // Goal Type Specific Display
    when (goalType) {
      GoalType.MAINTAIN -> {
        MaintainGoalDisplay(
          account = account,
          currentWeight = currentWeight,
          goalWeight = goalWeight,
          weightlessWeight = weightlessWeight,
        )
      }

      GoalType.LOSE_GAIN, GoalType.LOSE, GoalType.GAIN -> {
        LoseGainGoalDisplay(
          account = account,
          currentWeight = currentWeight,
          goalWeight = goalWeight,
          progressPercentage = displayProgressPercentage,
          weightlessWeight = weightlessWeight,
        )
      }
    }
  }
}

/**
 * Calculates goal percentage using the same logic as GoalService.getPercentComplete.
 * Based on Angular's getPercentComplete method - exact implementation.
 * @param goalType The goal type
 * @param initialWeight Initial weight in stored format
 * @param goalWeight Goal weight in stored format
 * @param latestWeight Latest weight in stored format
 * @return Percentage completion (0-100) or null if calculation not possible
 */
private fun calculateGoalPercentage(
  goalType: GoalType,
  initialWeight: Double,
  goalWeight: Double,
  latestWeight: Double,
): Double? {
  // Only calculate for lose/gain goals, maintain goals don't have percentage
  if (goalType == GoalType.MAINTAIN) return null
  var percent = 0
  var progressPercentage = 0.0
  if (latestWeight <= 0.0) return null // No valid weight data
  when (goalType) {
    GoalType.LOSE -> {
      progressPercentage = ((latestWeight - goalWeight) / (initialWeight - goalWeight))
      percent = 100 - floor(progressPercentage * 100).toInt()
    }

    GoalType.GAIN -> {
      progressPercentage = ((latestWeight - initialWeight) / (goalWeight - initialWeight))
      percent = floor(progressPercentage * 100).toInt()
    }

    else -> return null // Maintain goals don't have percentage
  }
  return if (percent < 0) 0.0 else percent.toDouble()
}

/**
 * Display for maintain goal - shows distance from goal weight.
 */
@Composable
private fun MaintainGoalDisplay(
  account: Account,
  currentWeight: Double,
  goalWeight: Double,
  isFromMilestone: Boolean = false,
  weightlessWeight: Float?,
) {
  val isWeightlessOn = account.isWeightlessOn ?: false
  val weightUnit = account.weightUnit
  val displayCurrentWeight =
      AccountHelper.processStoredWeightToDisplay((currentWeight * 10), weightUnit)
  val weightless = Weightless(isWeightlessOn, weightlessWeight ?: 0.0f)
  val displayGoalWeight = AccountHelper.processStoredWeightToDisplay(goalWeight, weightUnit)
  val distanceText =
    GoalDisplayHelper.computeToGoal(
      displayGoalWeight,
      if(isFromMilestone) currentWeight else displayCurrentWeight,
      weightless,
    )
    fun displayWeight(weight: Double): String {
      val processedWeight = if (isWeightlessOn && weightlessWeight != null) {
        (weight) - weightlessWeight
      } else {
        weight
      }
      val displayWeight = AccountHelper.processStoredWeightToDisplay(processedWeight, weightUnit)
      return if (isWeightlessOn && weightlessWeight != null) {
        // Only show symbols in weightless mode
        when {
          displayWeight > 0 -> "+${displayWeight}"
          displayWeight < 0 -> "$displayWeight" // Already includes minus sign
          else -> "$displayWeight"
        }
      } else {
        // No symbols when not in weightless mode
        "$displayWeight"
      }
    }

  AppStyledCard(
    modifier =
      Modifier
        .clip(shape = RoundedCornerShape(MeTheme.borderRadius.sm))
        .background(colorScheme.primaryBackground)
        .padding(vertical = 20.dp, horizontal = spacing.sm),
  ) {
    Row(
      verticalAlignment = Alignment.Bottom,
      horizontalArrangement = Arrangement.Center,
    ) {
      AppText(
        textType = TextType.CardTitle,
        text = distanceText,
      )
      AppText(
        text = "  ${weightUnit.label} ${GoalStrings.To} ${displayWeight(goalWeight)} ${GoalStrings.GoalWeight}",
        textType = TextType.ListSubtitle,
        modifier = Modifier.padding(bottom = spacing.xs),
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
  progressPercentage: Double,
  weightlessWeight: Float?,
) {
  val weightUnit = account.weightUnit.label
  val isWeightlessOn = account.isWeightlessOn ?: false
  val weightless = Weightless(isWeightlessOn, weightlessWeight ?: 0.0f)
  val displayGoalWeight = AccountHelper.processStoredWeightToDisplay(goalWeight, account.weightUnit)
  val displayCurrentWeight = AccountHelper.processStoredWeightToDisplay(currentWeight * 10, account.weightUnit)
  val isLoseGoal = displayGoalWeight < displayCurrentWeight
  if (isLoseGoal) GoalType.LOSE else GoalType.GAIN
  var toGoal = GoalDisplayHelper.computeToGoal(displayGoalWeight, displayCurrentWeight, weightless)
   fun displayWeight(weight: Double): String {
     val processedWeight = if (isWeightlessOn && weightlessWeight != null) {
       weight - weightlessWeight
     } else {
       weight
     }
     val displayWeight = AccountHelper.processStoredWeightToDisplay(processedWeight, account.weightUnit)
     return if (isWeightlessOn && weightlessWeight != null) {
       // Only show symbols in weightless mode
       when {
         displayWeight > 0 -> "+${displayWeight}"
         displayWeight < 0 -> "$displayWeight" // Already includes minus sign
         else -> "$displayWeight"
       }
     } else {
       // No symbols when not in weightless mode
       "$displayWeight"
     }
   }
  AppStyledCard(
    modifier =
      Modifier
        .clip(shape = RoundedCornerShape(MeTheme.borderRadius.sm))
        .background(colorScheme.primaryBackground)
        .padding(top = spacing.sm, bottom = 21.dp),
  ) {
    Row(
      verticalAlignment = Alignment.Bottom,
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = spacing.sm),
    ) {
      Row(verticalAlignment = Alignment.Bottom) {
        AppText(
          text =
            if (progressPercentage >= 100) {
              toGoal = 0.toString()
              toGoal
            } else toGoal,
          textType = TextType.CardTitle,
          // Always show negative for lose goal
          color = colorScheme.textHeading,
          modifier = Modifier.offset(y = 5.dp),
        )
        AppText(
          text = " $weightUnit ${GoalStrings.Goal}",
          textType = TextType.ListSubtitle,
          color = colorScheme.textBody,
          modifier = Modifier.padding(bottom = spacing.x4s),
        )
      }
      if(progressPercentage >= 100) {
        AppText(
          text = GoalStrings.GoalReached,
          textType = TextType.ListSubtitle,
          color = colorScheme.textBody,
          textAlign = TextAlign.End,
          modifier = Modifier.padding(bottom = spacing.x4s),
          // .padding(top = spacing.x3s),
        )
      }
    }
      // Progress Slider using AppLinearProgressIndicator
    AppLinearProgressIndicator(
      progress = (progressPercentage / 100.0).toFloat().coerceIn(0f, 1f),
      type = AppLinearProgressType.Success,
      height = 8.dp,
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(start = spacing.sm, end = spacing.sm, top = spacing.x3s, bottom = spacing.x4s),
    )
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = spacing.sm),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        AppText(
          text = displayWeight(account.initialWeight),
          textType = TextType.ListSubtitle,
          color = colorScheme.textHeading,
        )

        AppText(
          text = displayWeight(goalWeight),
          textType = TextType.ListSubtitle,
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
      val previewAccount =
        Account(
          id = "preview-id",
          firstName = "Preview",
          lastName = "User",
          dob = "1990-01-01",
          email = "preview@example.com",
          gender = "male",
          zipcode = "12345",
          weightUnit = WeightUnit.LB,
          height = 700,
          activityLevel = "normal",
          goalType = "maintain",
          goalWeight = 140.0,
          initialWeight = 160.0,
          goalPercent = 100.0,
          isWeightlessOn = false,
          weightlessWeight = null,
        )

      // Maintain Goal Preview
      GoalMilestoneDisplay(
        account = previewAccount,
        latestWeight = 150.0,
        modifier = Modifier.padding(spacing.md),
      )

      Spacer(modifier = Modifier.height(spacing.lg))

      // Lose/Gain Goal Preview
      val loseGainAccount =
        previewAccount.copy(
          goalType = "lose",
          goalPercent = 100.0,
        )
      GoalMilestoneDisplay(
        account = loseGainAccount,
        latestWeight = 150.0,
        modifier = Modifier.padding(spacing.md),
      )
    }
  }
}
