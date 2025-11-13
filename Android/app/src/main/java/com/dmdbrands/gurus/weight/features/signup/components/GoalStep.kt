package com.dmdbrands.gurus.weight.features.signup.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonData
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonGroup
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonSize
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonType
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.common.composition.LocalCardAlignment
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.common.helper.form.FormValidations
import com.dmdbrands.gurus.weight.features.common.helper.form.ValidationType
import com.dmdbrands.gurus.weight.features.signup.strings.SignupStrings
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Helper function to update weight validators when metric toggle changes
 */
private fun updateWeightValidators(
  control: FormControl<String>,
  isMetric: Boolean
) {
  // Remove existing weight and body comp validators
  control.removeValidator(ValidationType.NOT_IN_RANGE)

  // Add the appropriate weight validator based on unit
  val weightUnit = if (isMetric) WeightUnit.KG else WeightUnit.LB
  control.addValidator(FormValidations.weightValidator(weightUnit))

  // This prevents validation errors from showing when just switching units on untouched/empty fields
  if (control.touched && control.value.isNotEmpty()) {
    control.validate()
  }
}

/**
 * Step for collecting user's weight goals with metric/imperial toggle
 */
@Composable
fun GoalStep(
  title: String? = null,
  subtitle: String = SignupStrings.goalStepSubtitle,
  goalTypeControl: FormControl<String>,
  currentWeightControl: FormControl<String>,
  goalWeightControl: FormControl<String>,
  isMetric: Boolean = false,
  onGoalTypeChange: (GoalType) -> Unit = {},
  onNext: () -> Unit = {},
  showCurrentWeightForMaintain: Boolean = true,
  initialWeightUnit: WeightUnit? = null,
) {
  val currentWeightFocusRequester = remember { FocusRequester() }
  val goalWeightFocusRequester = remember { FocusRequester() }

  val weightUnit = if (isMetric) WeightUnit.KG.label else WeightUnit.LB.label

  // // Initialize weight validators based on initial weight unit or metric toggle
  // LaunchedEffect(isMetric, initialWeightUnit) {
  //   updateWeightValidators(currentWeightControl, isMetric)
  //   updateWeightValidators(goalWeightControl, isMetric)
  // }

  // Goal type options
  val goalTypeOptions =
    listOf(
      SegmentButtonData(id = 0, label = SignupStrings.goalStepMaintain),
      SegmentButtonData(id = 1, label = SignupStrings.goalStepLoseGain),
    )

  AppStyledCard(
    cardAlignmentType = LocalCardAlignment.current,
  ) {
    title?.let {
      AppText(title, TextType.Title, spacing = MeTheme.spacing.xs)
    }
    AppText(subtitle, TextType.Subtitle, spacing = MeTheme.spacing.lg)
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceAround,
    ) {
      val selectedOption = when (goalTypeControl.value) {
        GoalType.MAINTAIN.value -> goalTypeOptions[0]
        GoalType.LOSE_GAIN.value -> goalTypeOptions[1]
        else -> goalTypeOptions[1] // Fallback to Lose/Gain if unknown
      }
      SegmentButtonGroup(
        data = goalTypeOptions,
        selectedData = selectedOption,
        onSelected = { selectedOption ->
          val goalType = if (selectedOption.id == 0) GoalType.MAINTAIN else GoalType.LOSE_GAIN
          val value = goalType.value
          goalTypeControl.onValueChange(value)
          onGoalTypeChange(goalType) // Trigger the callback
        },
        size = SegmentButtonSize.Small,
        type = SegmentButtonType.Scrollable,
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
        // Enable for any non-maintain variant (lose, gain, lose_gain)
        enabled = goalTypeControl.value != GoalType.MAINTAIN.value,
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
      onGoalTypeChange = {},
      onNext = {},
      title = SignupStrings.goalStepTitle,
    )
  }
}
