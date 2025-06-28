package com.greatergoods.meapp.features.manualEntry

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.core.navigation.LocalNavBackStack
import com.greatergoods.meapp.domain.model.common.DashboardType
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
import com.greatergoods.meapp.features.manualEntry.components.ExpandableMetricsCard
import com.greatergoods.meapp.features.manualEntry.strings.EntryScreenStrings
import com.greatergoods.meapp.features.manualEntry.viewmodel.EntryIntent
import com.greatergoods.meapp.features.manualEntry.viewmodel.EntryState
import com.greatergoods.meapp.features.manualEntry.viewmodel.EntryViewModel
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme
import java.util.Calendar

@Composable
fun EntryScreen() {
    val viewModel: EntryViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    LocalNavBackStack.current
    EntryScreenContent(state, viewModel::handleIntent)

    LaunchedEffect(Unit) {
        viewModel.initDeactivate()
    }

    /* // Register canDeactivate callback for this screen
     LaunchedEffect(backStack, state.form.isDirty) {
         backStack.registerCanDeactivate(AppRoute.Main.Entry) {
             if (state.form.controls.weightDateTime.weight.dirty) {
                 suspendCancellableCoroutine { cont ->
                     viewModel.dialogQueueService.enqueue(
                         DialogModel.Confirm(
                             title = AppPopupStrings.UnsavedChanges.ManualEntryTitle,
                             message = AppPopupStrings.UnsavedChanges.Message,
                             onConfirm = { cont.resume(true) },
                             onCancel = { cont.resume(false) },
                         ),
                     )
                 }
             } else {
                 true
             }
         }
     }
     // Unregister on dispose
     DisposableEffect(backStack) {
         onDispose {
             backStack.unregisterCanDeactivate(AppRoute.Main.Entry)
         }
     }*/
}

@Composable
private fun EntryScreenContent(
    state: EntryState,
    handleIntent: (EntryIntent) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val entryForm = state.form.forms
    val scrollState = rememberScrollState()
    val calendar = Calendar.getInstance()
    val maxValue =
        DateTimeValue.DateTime(
            millis = calendar.timeInMillis,
            hour = calendar.get(Calendar.HOUR_OF_DAY),
            minute = calendar.get(Calendar.MINUTE),
        )
    val interactionSource = remember { MutableInteractionSource() }
    val weightFocusRequester = remember { FocusRequester() }

    AppScaffold(EntryScreenStrings.Title) {
        Column(
            modifier =
                Modifier
                    .verticalScroll(scrollState)
                    .padding(horizontal = MeTheme.spacing.sm)
                    .padding(top = MeTheme.spacing.md)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { focusManager.clearFocus() },
                    ),
            verticalArrangement = Arrangement.Top,
        ) {
            AppInput(
                formControl = entryForm.weightDateTime.controls.weight,
                label = EntryScreenStrings.WEIGHT_LABEL,
                type = AppInputType.BODY_COMP,
                imeAction = ImeAction.Next,
                onImeAction = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .focusRequester(weightFocusRequester),
            )
            DateTimeInput(
                formControl = entryForm.weightDateTime.controls.dateTime,
                mode = DateTimeInputMode.DateTime,
                label = EntryScreenStrings.DATE_LABEL,
                maxValue = maxValue,
            )
            Spacer(modifier = Modifier.height(MeTheme.spacing.xl))
            // Metrics section as a single expandable card
            ExpandableMetricsCard(
                title = EntryScreenStrings.METRICS_SECTION_TITLE,
                subheading = EntryScreenStrings.METRICS_SECTION_SUBHEADING,
                generalMetrics = entryForm.generalMetrics.controls,
                r4ScaleMetrics = entryForm.r4ScaleMetrics?.controls,
                onImeAction = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                },
                dashboardType = DashboardType.DASHBOARD_12_METRICS,
            )
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                AppButton(
                    enabled = state.form.isValid,
                    label = EntryScreenStrings.SaveButton,
                    size = ButtonSize.Large,
                    type = ButtonType.PrimaryFilled,
                    onClick = {
                        keyboardController?.hide()
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
