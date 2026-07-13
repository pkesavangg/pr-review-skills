package com.dmdbrands.gurus.weight.features.manualEntry.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.dmdbrands.gurus.weight.core.shared.utilities.testing.TestTags
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import com.dmdbrands.gurus.weight.features.common.components.AppInput
import com.dmdbrands.gurus.weight.features.common.components.AppInputType
import com.dmdbrands.gurus.weight.features.common.components.AppTextArea
import com.dmdbrands.gurus.weight.features.common.components.DateTimeInput
import com.dmdbrands.gurus.weight.features.common.components.DateTimeInputMode
import com.dmdbrands.gurus.weight.features.manualEntry.strings.EntryScreenStrings
import com.dmdbrands.gurus.weight.features.manualEntry.viewmodel.BloodPressureFormControls
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Blood pressure entry section — systolic, diastolic, pulse, notes, and date/time.
 */
@Composable
fun BloodPressureSection(
    controls: BloodPressureFormControls,
    onImeAction: () -> Unit,
) {
    val systolicFocusRequester = remember { FocusRequester() }
    val diastolicFocusRequester = remember { FocusRequester() }
    val pulseFocusRequester = remember { FocusRequester() }

    AppInput(
        formControl = controls.systolic,
        label = EntryScreenStrings.SYSTOLIC_LABEL,
        type = AppInputType.NUMERIC_STRING,
        imeAction = ImeAction.Next,
        nextFocusRequester = diastolicFocusRequester,
        maxLength = 3,
        testTag = TestTags.ManualEntry.BpSystolicField,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(systolicFocusRequester),
    )
    AppInput(
        formControl = controls.diastolic,
        label = EntryScreenStrings.DIASTOLIC_LABEL,
        type = AppInputType.NUMERIC_STRING,
        imeAction = ImeAction.Next,
        nextFocusRequester = pulseFocusRequester,
        maxLength = 3,
        testTag = TestTags.ManualEntry.BpDiastolicField,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(diastolicFocusRequester),
    )
    AppInput(
        formControl = controls.pulse,
        label = EntryScreenStrings.PULSE_LABEL,
        type = AppInputType.NUMERIC_STRING,
        imeAction = ImeAction.Done,
        onImeAction = onImeAction,
        maxLength = 3,
        testTag = TestTags.ManualEntry.BpPulseField,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(pulseFocusRequester),
    )
    Spacer(modifier = Modifier.height(MeTheme.spacing.xs))
    AppTextArea(
        formControl = controls.notes,
        label = EntryScreenStrings.NOTES_LABEL,
        maxLength = EntryScreenStrings.NOTES_MAX_LENGTH,
        showCharacterCounter = true,
    )
    Spacer(modifier = Modifier.height(MeTheme.spacing.md))
    DateTimeInput(
        formControl = controls.dateTime,
        dateTestTag = TestTags.ManualEntry.DateButton,
        timeTestTag = TestTags.ManualEntry.TimeButton,
        mode = DateTimeInputMode.DateTime,
        label = EntryScreenStrings.DATE_LABEL,
        maxValue = null,
    )
}
