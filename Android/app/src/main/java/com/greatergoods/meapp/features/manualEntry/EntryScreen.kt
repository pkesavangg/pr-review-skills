package com.greatergoods.meapp.features.manualEntry

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.navigation.LocalNavBackStack
import com.greatergoods.meapp.features.common.components.AppButton
import com.greatergoods.meapp.features.common.components.AppInput
import com.greatergoods.meapp.features.common.components.AppInputType
import com.greatergoods.meapp.features.common.components.AppScaffold
import com.greatergoods.meapp.features.common.components.ButtonSize
import com.greatergoods.meapp.features.common.components.ButtonType
import com.greatergoods.meapp.features.common.components.DateTimeInput
import com.greatergoods.meapp.features.common.components.DateTimeInputMode
import com.greatergoods.meapp.features.common.components.DateTimeValue
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.model.DialogModel
import com.greatergoods.meapp.features.manualEntry.components.ExpandableMetricsCard
import com.greatergoods.meapp.features.manualEntry.strings.EntryScreenStrings
import com.greatergoods.meapp.features.manualEntry.viewmodel.EntryIntent
import com.greatergoods.meapp.features.manualEntry.viewmodel.EntryState
import com.greatergoods.meapp.features.manualEntry.viewmodel.EntryViewModel
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

@Composable
fun EntryScreen() {
    val viewModel: EntryViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    val backStack = LocalNavBackStack.current
    EntryScreenContent(state, viewModel::handleIntent)

    if (state.form.controls.weightDateTime.weight.dirty) {
        BackHandler {
            viewModel.dialogQueueService.enqueue(
                DialogModel.Confirm(
                    title = "Alert",
                    message = "Are you sure you want to discard changes?",
                    onConfirm = {
                        backStack.removeLast(AppRoute.Home)
                    },
                ),
            )
        }
    }
}

@Composable
private fun EntryScreenContent(
    state: EntryState,
    handleIntent: (EntryIntent) -> Unit,
) {
    val keyBoardController = LocalSoftwareKeyboardController.current
    val controls = state.form.controls
    val scrollState = rememberScrollState()
    AppScaffold(EntryScreenStrings.Title) {
        Column(
            modifier =
                Modifier
                    .verticalScroll(scrollState)
                    .padding(horizontal = MeTheme.spacing.sm)
                    .padding(top = MeTheme.spacing.md),
            verticalArrangement = Arrangement.Top,
        ) {
            AppInput(
                formControl = controls.weightDateTime.weight,
                label = EntryScreenStrings.WEIGHT_LABEL.plus(state.weightMode),
                type = AppInputType.NUMBER,
                modifier = Modifier.fillMaxWidth(),
            )
            DateTimeInput(
                formControl = controls.weightDateTime.dateTime,
                mode = DateTimeInputMode.DateTime,
                label = EntryScreenStrings.DATE_LABEL,
                maxValue = DateTimeValue.Date(System.currentTimeMillis()),
            )
            Spacer(modifier = Modifier.height(MeTheme.spacing.xl))
            // Metrics section as a single expandable card
            ExpandableMetricsCard(
                title = EntryScreenStrings.METRICS_SECTION_TITLE,
                subheading = EntryScreenStrings.METRICS_SECTION_SUBHEADING,
                generalMetrics = controls.generalMetrics,
                r4ScaleMetrics = controls.r4ScaleMetrics,
            )
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                AppButton(
                    enabled = controls.weightDateTime.weight.validate(),
                    label = EntryScreenStrings.SaveButton,
                    size = ButtonSize.Large,
                    type = ButtonType.PrimaryFilled,
                    onClick = {
                        keyBoardController?.hide()
                        handleIntent(EntryIntent.Save)
                    },
                )
            }
            Spacer(modifier = Modifier.height(MeTheme.spacing.x3l))
        }
    }
}

@PreviewTheme
@Composable
fun EntryScreenPreview() {
    MeAppTheme {
        EntryScreen()
    }
}
