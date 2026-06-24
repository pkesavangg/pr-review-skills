package com.dmdbrands.gurus.weight.features.goal.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.AppIconButton
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.ButtonSize
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.helper.AccountHelper.isMetricUnit
import com.dmdbrands.gurus.weight.features.common.components.dismissKeyboardOnTap
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup
import com.dmdbrands.gurus.weight.features.goal.components.GoalMilestoneDisplay
import com.dmdbrands.gurus.weight.features.goal.model.GoalFormControls
import com.dmdbrands.gurus.weight.features.goal.model.GoalIntent
import com.dmdbrands.gurus.weight.features.goal.model.GoalState
import com.dmdbrands.gurus.weight.features.goal.strings.GoalStrings
import com.dmdbrands.gurus.weight.features.goal.viewmodel.GoalViewModel
import com.dmdbrands.gurus.weight.features.signup.components.GoalStep
import com.dmdbrands.gurus.weight.features.signup.strings.SignupStrings
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing

/**
 * Goal screen composable. Displays the goal form, handles user input, and shows loading/error states.
 */
@Composable
fun GoalScreen() {
  val viewmodel: GoalViewModel = hiltViewModel()
  val state by viewmodel.state.collectAsStateWithLifecycle()
  BackHandler {
    viewmodel.handleIntent(GoalIntent.OnBack)
  }
  GoalContent(state, viewmodel::handleIntent)
}

@Composable
private fun GoalContent(state: GoalState, handleIntent: (GoalIntent) -> Unit) {
  val keyboardController = LocalSoftwareKeyboardController.current
  val focusManager = LocalFocusManager.current
  val scrollState = rememberScrollState()
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
        enabled =  state.form.isDirty && state.form.controls.isValidForGoalType(),
      ) {
        keyboardController?.hide()
        handleIntent.invoke(GoalIntent.Submit)
      }
    },

  ) { scaffoldModifier ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(scrollState)
        .dismissKeyboardOnTap(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Top,
    ) {
      val canDisplayMilestone =
        state.account?.goalType != null && state.account.goalWeight != null && state.account.goalWeight > 0
      val canShowTitle = state.account?.goalType != null
      // Milestone Display Section - showing current goal progress
      if (canDisplayMilestone) {
        GoalMilestoneDisplay(
          account = state.account,
          latestWeight = state.latestWeight,
          modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.md),
        )
      }
      Spacer(modifier = Modifier.padding(top = if (!canDisplayMilestone) spacing.md else 0.dp))

      GoalStep(
        title = if (canShowTitle) GoalStrings.Title else SignupStrings.goalStepTitle,
        subtitle = GoalStrings.Subtitle,
        goalTypeControl = state.form.controls.goalType,
        currentWeightControl = state.form.controls.startingWeight,
        goalWeightControl = state.form.controls.goalWeight,
        onGoalTypeChange = { goalType ->
          handleIntent(GoalIntent.ChangeGoalType(goalType))
        },
        onNext = {
          keyboardController?.hide()
          focusManager.clearFocus()
        },
        // For Goal screen, show current weight field and enable unless MAINTAIN
        showCurrentWeightForMaintain = false,
        isMetric = state.account?.isMetricUnit() ?: false,
        initialWeightUnit = state.account?.let { account ->
          if (account.isMetricUnit()) WeightUnit.KG else WeightUnit.LB
        },
      )
      Spacer(modifier = Modifier.padding(spacing.lg))
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
          FormControl.create(
            initialValue = "maintain",
          ),
          FormControl.create(
            initialValue = "150.0",
            validators = emptyList(),
          ),
          FormControl.create(
            initialValue = "140.0",
            validators = emptyList(),
          ),
        ),
      ),
    )
    GoalContent(dummyGoalState) {}
  }
}
