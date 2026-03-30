package com.dmdbrands.gurus.weight.features.manualEntry.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import com.dmdbrands.gurus.weight.features.common.components.AppInput
import com.dmdbrands.gurus.weight.features.common.components.AppInputType
import com.dmdbrands.gurus.weight.features.common.components.DateTimeInput
import com.dmdbrands.gurus.weight.features.common.components.DateTimeInputMode
import com.dmdbrands.gurus.weight.features.manualEntry.strings.EntryScreenStrings
import com.dmdbrands.gurus.weight.features.manualEntry.viewmodel.EntryState
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Weight entry section — weight input, date/time, and expandable body metrics card.
 */
@Composable
fun WeightEntrySection(
    state: EntryState,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val weightFocusRequester = remember { FocusRequester() }
    val entryForm = state.form.forms

    AppInput(
        formControl = entryForm.weightDateTime.controls.weight,
        label = EntryScreenStrings.WEIGHT_LABEL.plus(" (${state.weightMode.label})"),
        type = AppInputType.BODY_COMP,
        imeAction = ImeAction.Next,
        onImeAction = {
            focusManager.clearFocus()
            keyboardController?.hide()
        },
        maxLength = 4,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(weightFocusRequester),
    )
    DateTimeInput(
        formControl = entryForm.weightDateTime.controls.dateTime,
        mode = DateTimeInputMode.DateTime,
        label = EntryScreenStrings.DATE_LABEL,
        maxValue = null,
    )
    Spacer(modifier = Modifier.height(MeTheme.spacing.xl))
    ExpandableMetricsCard(
        title = EntryScreenStrings.METRICS_SECTION_TITLE,
        subheading = EntryScreenStrings.METRICS_SECTION_SUBHEADING,
        generalMetrics = entryForm.generalMetrics.controls,
        r4ScaleMetrics = entryForm.r4ScaleMetrics?.controls,
        expandedInitially = state.isMetricFieldsExpandedInitially,
        onImeAction = {
            focusManager.clearFocus()
            keyboardController?.hide()
        },
        dashboardType = state.dashboardType,
    )
}
