package com.dmdbrands.gurus.weight.features.signup.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import com.dmdbrands.gurus.weight.domain.enums.GoalType
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.features.common.components.AppInput
import com.dmdbrands.gurus.weight.features.common.components.AppInputDefaults
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
import com.dmdbrands.gurus.weight.features.signup.strings.SignupStrings
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing

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
  onMetricToggle: (Boolean) -> Unit = {},
  showCurrentWeightForMaintain: Boolean = true,
  showUnitSegment: Boolean = false,
  initialWeightUnit: WeightUnit? = null,
) {
  val currentWeightFocusRequester = remember { FocusRequester() }
  val goalWeightFocusRequester = remember { FocusRequester() }

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
    title?.let {
      // TalkBack: the step title is a heading for by-heading navigation.
      AppText(
        title,
        TextType.Title,
        spacing = MeTheme.spacing.xs,
        modifier = Modifier.semantics { heading() },
      )
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
        spacedBy = spacing.lg
      )
    }
    Spacer(modifier = Modifier.padding(vertical = MeTheme.spacing.sm))
    val shouldShowCurrentWeight =
      if (goalTypeControl.value == GoalType.MAINTAIN.value) {
        showCurrentWeightForMaintain
      } else {
        true // Always show for lose/gain
      }

    AnimatedVisibility(visible = shouldShowCurrentWeight) {
      Column {
        AppInput(
          formControl = currentWeightControl,
          type = AppInputType.BODY_COMP,
          label = SignupStrings.goalStepCurrentWeightLabel,
          trailingText = weightUnit,
          showTrailingIcon = false,
          imeAction = ImeAction.Next,
          nextFocusRequester = goalWeightFocusRequester,
          testTag = "starting_weight_input",
          modifier = Modifier.focusRequester(currentWeightFocusRequester),
          enabled = goalTypeControl.value != GoalType.MAINTAIN.value,
          maxLength = 4,
        )
        Spacer(modifier = Modifier.height(MeTheme.spacing.xs))
      }
    }

    AppInput(
      formControl = goalWeightControl,
      maxLength = 4,
      type = AppInputType.BODY_COMP,
      label = SignupStrings.goalStepGoalWeightLabel,
      trailingText = weightUnit,
      showTrailingIcon = false,
      imeAction = ImeAction.Next,
      onImeAction = onNext,
      testTag = "goal_weight_input",
      modifier =
        if (shouldShowCurrentWeight) {
          Modifier.focusRequester(goalWeightFocusRequester)
        } else {
          Modifier.focusRequester(currentWeightFocusRequester)
        },
    )

    if (showUnitSegment) {
      val weightUnitOptions = listOf(
        SegmentButtonData(id = 0, label = SignupStrings.weightUnitLbs),
        SegmentButtonData(id = 1, label = SignupStrings.weightUnitKg),
      )
      val selectedUnitOption = if (isMetric) weightUnitOptions[1] else weightUnitOptions[0]
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
      ) {
        SegmentButtonGroup(
          data = weightUnitOptions,
          selectedData = selectedUnitOption,
          key = SegmentButtonData::label,
          onSelected = { option -> onMetricToggle(option.id == 1) },
          size = SegmentButtonSize.Small,
          type = SegmentButtonType.Scrollable,
          spacedBy = MeTheme.spacing.xs,
          uppercaseLabels = false,
        )
      }
    }
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
