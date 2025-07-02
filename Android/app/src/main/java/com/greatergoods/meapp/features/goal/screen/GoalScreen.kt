package com.greatergoods.meapp.features.goal.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.features.common.components.AppButton
import com.greatergoods.meapp.features.common.components.AppIconButton
import com.greatergoods.meapp.features.common.components.AppScaffold
import com.greatergoods.meapp.features.common.components.ButtonSize
import com.greatergoods.meapp.features.common.components.ButtonType
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.features.common.helper.form.FormGroup
import com.greatergoods.meapp.features.goal.components.GoalMilestoneDisplay
import com.greatergoods.meapp.features.goal.model.GoalFormControls
import com.greatergoods.meapp.features.goal.model.GoalIntent
import com.greatergoods.meapp.features.goal.model.GoalState
import com.greatergoods.meapp.features.goal.strings.GoalStrings
import com.greatergoods.meapp.features.goal.viewmodel.GoalViewModel
import com.greatergoods.meapp.features.signup.components.GoalStep
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme.colorScheme
import com.greatergoods.meapp.theme.MeTheme.spacing

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
                enabled = state.form.isValid && state.form.isDirty,
            ) {
                keyboardController?.hide()
                handleIntent.invoke(GoalIntent.Submit)
            }
        },
        containerColor = colorScheme.secondaryBackground,
        appBarColor = colorScheme.secondaryBackground,
    ) { scaffoldModifier ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            val canDisplayMilestone = state.account?.goalType != null && state.account.goalWeight != null && state.account.goalWeight > 0
            // Milestone Display Section - showing current goal progress
            if (canDisplayMilestone) {
                GoalMilestoneDisplay(
                    account = state.account,
                    latestWeight = state.latestWeight,
                    modifier = Modifier.padding(bottom = spacing.md)
                )
            }
            Spacer(modifier = Modifier.padding(top = if(!canDisplayMilestone) spacing.md else 0.dp))
            GoalStep(
                subtitle = GoalStrings.Subtitle,
                goalTypeControl = state.form.controls.goalType,
                currentWeightControl = state.form.controls.currentWeight,
                goalWeightControl = state.form.controls.goalWeight,
                onGoalTypeChange = { goalType ->
                    handleIntent(GoalIntent.ChangeGoalType(goalType))
                },
                onNext = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                },
                showCurrentWeightForMaintain = false,
                showMetricToggle = false
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
                        initialValue = "maintain"
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
