package com.greatergoods.meapp.features.goal.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.core.shared.utilities.ConversionTools
import com.greatergoods.meapp.features.common.components.AppButton
import com.greatergoods.meapp.features.common.components.AppIconButton
import com.greatergoods.meapp.features.common.components.AppScaffold
import com.greatergoods.meapp.features.common.components.AppText
import com.greatergoods.meapp.features.common.components.ButtonSize
import com.greatergoods.meapp.features.common.components.ButtonType
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.components.TextType
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.features.common.helper.form.FormGroup
import com.greatergoods.meapp.features.goal.model.GoalFormControls
import com.greatergoods.meapp.features.goal.model.GoalIntent
import com.greatergoods.meapp.features.goal.model.GoalState
import com.greatergoods.meapp.features.goal.strings.GoalStrings
import com.greatergoods.meapp.features.goal.viewmodel.GoalViewModel
import com.greatergoods.meapp.features.signup.components.GoalStep
import com.greatergoods.meapp.features.signup.model.GoalType
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme.colorScheme
import com.greatergoods.meapp.theme.MeTheme.spacing
import kotlin.math.abs
import kotlin.math.min

/**
 * Goal screen composable. Displays the goal form, handles user input, and shows loading/error states.
 */
@Composable
fun GoalScreen() {
    val viewmodel: GoalViewModel = hiltViewModel()
    val state by viewmodel.state.collectAsState()
    BackHandler {
        viewmodel.handleIntent(GoalIntent.OnBack)
    }
    GoalContent(state, viewmodel::handleIntent)
}

@Composable
private fun GoalContent(state: GoalState, handleIntent: (GoalIntent) -> Unit) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }
    val scrollState = rememberScrollState()

    // Get weight unit from state
    val weightUnit = state.weightUnit

    AppScaffold(
        title = GoalStrings.PageTitle,
        navigationIcon = {
            AppIconButton(AppIcons.Default.Close) { handleIntent(GoalIntent.OnBack) }
        },
        actions = {
            AppButton(
                GoalStrings.SaveGoalButton,
                type = ButtonType.InlineTextPrimary,
                size = ButtonSize.Small,
                enabled = state.form.isValid && ( state.form.isDirty || state.hasToggleChanged),
            ) {
                keyboardController?.hide()
                handleIntent.invoke(GoalIntent.Submit)
            }
        },
        containerColor = colorScheme.secondaryBackground,
        appBarColor = colorScheme.secondaryBackground,
        borderColor = Color.Transparent,
    ) { scaffoldModifier ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            // Milestone Display Section - showing current goal progress
            if (state.account?.goalType != null && state.account.goalWeight != null) {
                GoalMilestoneDisplay(
                    currentWeight = state.latestWeight ?: state.account.initialWeight,
                    goalWeight = state.account.goalWeight,
                    initialWeight = state.account.initialWeight,
                    goalType = state.account.goalType,
                    goalPercent = state.account.goalPercent,
                    weightUnit = weightUnit,
                    isMetric = state.isMetric,
                    isWeightlessOn = state.account.isWeightlessOn ?: false,
                    weightlessWeight = state.account.weightlessWeight,
                    modifier = Modifier.padding(bottom = spacing.md)
                )
            }

            GoalStep(
                title = GoalStrings.Title,
                subtitle = GoalStrings.Subtitle,
                goalTypeControl = state.form.controls.goalType,
                currentWeightControl = state.form.controls.currentWeight,
                goalWeightControl = state.form.controls.goalWeight,
                useMetricControl = state.form.controls.useMetric,
                onMetricToggle = { isMetric ->
                    handleIntent(GoalIntent.ToggleMetric(isMetric))
                },
                onNext = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                },
                showCurrentWeightForMaintain = false, // Hide current weight for maintain in goal settings
            )
            Spacer(modifier = Modifier.padding(spacing.lg))
        }
    }
}

/**
 * Weight formatting utility that mirrors the Angular WeightPipe behavior.
 * Handles weightless functionality and symbol formatting for weight display.
 *
 * @param value Weight value in stored format
 * @param showSymbol Whether to show + prefix for positive values
 * @param isMetric Whether to use metric units
 * @param isWeightlessOn Whether weightless mode is enabled
 * @param weightlessWeight Weight to subtract when weightless mode is enabled
 * @return Formatted weight string
 */
private fun formatWeight(
    value: Double,
    showSymbol: Boolean = false,
    isMetric: Boolean = false,
    isWeightlessOn: Boolean = false,
    weightlessWeight: Float? = null
): String {
    val weight = if (isWeightlessOn && weightlessWeight != null) {
        ConversionTools.convertStoredToDisplay(value - weightlessWeight, isMetric)
    } else {
        ConversionTools.convertStoredToDisplay(value, isMetric)
    }

    return if (showSymbol) {
        "${if (weight > 0) "+" else ""}${String.format("%.1f", weight)}"
    } else {
        String.format("%.1f", weight)
    }
}

/**
 * Computes the distance to goal for display, following the Angular computeToGoal pattern.
 * For maintain goals, returns formatted string with +/- indicating distance from goal weight.
 * For lose/gain goals, returns the absolute distance to goal.
 *
 * @param currentWeight Current weight value
 * @param goalWeight Goal weight value
 * @param goalType Goal type ("maintain", "lose", "gain", etc.)
 * @return Formatted string representing distance to goal
 */
private fun computeGoal(currentWeight: Double, goalWeight: Double, goalType: String): String {
    val toGoal = goalWeight - currentWeight

    return when (goalType) {
        GoalType.MAINTAIN.value, "maintain" -> {
            if (toGoal == 0.0) {
                return "0.0"
            }
            val weightAwayFromGoalWeight = 0 - toGoal
            when {
                weightAwayFromGoalWeight > 0 -> "+${String.format("%.1f", abs(weightAwayFromGoalWeight))}"
                else -> String.format("%.1f", weightAwayFromGoalWeight)
            }
        }
        else -> {
            // For lose/gain goals, return absolute distance
            String.format("%.1f", abs(toGoal))
        }
    }
}

/**
 * Milestone display composable showing current goal progress similar to Angular milestone page.
 */
@Composable
private fun GoalMilestoneDisplay(
    currentWeight: Double,
    goalWeight: Double,
    initialWeight: Double,
    goalType: String,
    goalPercent: Double,
    weightUnit: String,
    isMetric: Boolean,
    isWeightlessOn: Boolean,
    weightlessWeight: Float?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Current Weight Display
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Center
        ) {
            AppText(
                text = formatWeight(
                    value = currentWeight,
                    showSymbol = false,
                    isMetric = isMetric,
                    isWeightlessOn = isWeightlessOn,
                    weightlessWeight = weightlessWeight
                ),
                textType = TextType.Title,
                color = colorScheme.textHeading
            )
            Spacer(modifier = Modifier.width(spacing.xs))
            AppText(
                text = weightUnit,
                textType = TextType.Subtitle,
                color = colorScheme.textHeading
            )
        }
        // Goal Type Specific Display
        when (goalType) {
            GoalType.MAINTAIN.value -> {
                MaintainGoalDisplay(
                    currentWeight = currentWeight,
                    goalWeight = goalWeight,
                    weightUnit = weightUnit,
                    isMetric = isMetric,
                    isWeightlessOn = isWeightlessOn,
                    weightlessWeight = weightlessWeight
                )
            }
            GoalType.LOSE_GAIN.value, "losegain", "lose", "gain" -> {
                LoseGainGoalDisplay(
                    currentWeight = currentWeight,
                    goalWeight = goalWeight,
                    initialWeight = initialWeight,
                    goalPercent = goalPercent,
                    weightUnit = weightUnit,
                    isMetric = isMetric,
                    isWeightlessOn = isWeightlessOn,
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
    currentWeight: Double,
    goalWeight: Double,
    weightUnit: String,
    isMetric: Boolean,
    isWeightlessOn: Boolean,
    weightlessWeight: Float?
) {
    // Compute distance using actual display weights (considering weightless mode)
    val displayCurrentWeight = if (isWeightlessOn && weightlessWeight != null) {
        ConversionTools.convertStoredToDisplay(currentWeight - weightlessWeight, isMetric)
    } else {
        ConversionTools.convertStoredToDisplay(currentWeight, isMetric)
    }
    val displayGoalWeight = ConversionTools.convertStoredToDisplay(goalWeight, isMetric)
    val distanceText = computeGoal(displayCurrentWeight, displayGoalWeight, GoalType.MAINTAIN.value)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppText(
            text = "$distanceText$weightUnit ${GoalStrings.ToGoal} ${formatWeight(
                value = goalWeight,
                showSymbol = false,
                isMetric = isMetric,
                isWeightlessOn = false, // Goal weight is not affected by weightless mode
                weightlessWeight = null
            )}$weightUnit ${GoalStrings.GoalWeight}",
            textType = TextType.Body,
        )
    }
}

/**
 * Display for lose/gain goal - shows progress bar and remaining weight.
 */
@Composable
private fun LoseGainGoalDisplay(
    currentWeight: Double,
    goalWeight: Double,
    initialWeight: Double,
    goalPercent: Double,
    weightUnit: String,
    isMetric: Boolean,
    isWeightlessOn: Boolean,
    weightlessWeight: Float?
) {
    // Compute distance using actual display weights (considering weightless mode)
    val displayCurrentWeight = if (isWeightlessOn && weightlessWeight != null) {
        ConversionTools.convertStoredToDisplay(currentWeight - weightlessWeight, isMetric)
    } else {
        ConversionTools.convertStoredToDisplay(currentWeight, isMetric)
    }

    val displayGoalWeight = ConversionTools.convertStoredToDisplay(goalWeight, isMetric)
    val toGoal = computeGoal(displayCurrentWeight, displayGoalWeight, "losegain")
    val progressPercentage = min(100.0, goalPercent)
    val isGoalReached = goalPercent >= 100.0

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Progress Status Text
        if (isGoalReached) {
            AppText(
                text = GoalStrings.GoalReached,
                textType = TextType.Body,
                color = colorScheme.textHeading,
            )
        } else {
            AppText(
                text = "$toGoal$weightUnit ${GoalStrings.ToGoal}",
                textType = TextType.Body,
                color = colorScheme.textBody,
            )
        }

        Spacer(modifier = Modifier.height(spacing.md))

        // Progress Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(colorScheme.secondaryBackground),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = (progressPercentage / 100.0).toFloat())
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(colorScheme.primaryAction),
            )
        }

        Spacer(modifier = Modifier.height(spacing.md))

        // Weight Range Display
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            AppText(
                text = "${formatWeight(
                    value = initialWeight,
                    showSymbol = false,
                    isMetric = isMetric,
                    isWeightlessOn = false, // Initial weight is not affected by weightless mode
                    weightlessWeight = null
                )}$weightUnit",
                textType = TextType.Body,
                color = colorScheme.textHeading,
            )

            AppText(
                text = "${formatWeight(
                    value = goalWeight,
                    showSymbol = false,
                    isMetric = isMetric,
                    isWeightlessOn = false, // Goal weight is not affected by weightless mode
                    weightlessWeight = null
                )}$weightUnit",
                textType = TextType.Body,
                color = colorScheme.textHeading,
            )
        }
    }
}

@PreviewTheme
@Composable
fun GoalScreenPreview() {
    MeAppTheme {
        val dummyGoalState = GoalState(
            form = FormGroup(
                controls = GoalFormControls(
                    currentWeight = FormControl.create(
                        initialValue = "150.0",
                        validators = emptyList(),
                    ),
                    goalWeight = FormControl.create(
                        initialValue = "140.0",
                        validators = emptyList(),
                    ),
                    goalType = TODO(),
                    useMetric = TODO(),
                ),
            ),
            goalType = GoalType.LOSE_GAIN,
            weightUnit = "lbs",
            isMetric = false,
        )
        GoalContent(dummyGoalState) {}
    }
}
