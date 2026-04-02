package com.dmdbrands.gurus.weight.features.manualEntry.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import com.dmdbrands.gurus.weight.features.common.components.AppInput
import com.dmdbrands.gurus.weight.features.common.components.AppInputType
import com.dmdbrands.gurus.weight.features.common.components.AppTextArea
import com.dmdbrands.gurus.weight.features.common.components.DateTimeInput
import com.dmdbrands.gurus.weight.features.common.components.DateTimeInputMode
import com.dmdbrands.gurus.weight.features.manualEntry.strings.EntryScreenStrings
import com.dmdbrands.gurus.weight.features.manualEntry.viewmodel.BabyEntryFormControls
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Baby entry section — pounds + ounces (side-by-side), inches, notes, and date/time.
 */
@Composable
fun BabyEntrySection(
    controls: BabyEntryFormControls,
    onImeAction: () -> Unit,
) {
    val poundsFocusRequester = remember { FocusRequester() }
    val ouncesFocusRequester = remember { FocusRequester() }
    val inchesFocusRequester = remember { FocusRequester() }

    // Pounds + Ounces side-by-side (short input layout)
    // Each AppInput is wrapped in a Column so its error text + spacer stay
    // within its half of the Row (InputFieldBase emits multiple composables).
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MeTheme.spacing.md),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            AppInput(
                formControl = controls.pounds,
                label = EntryScreenStrings.POUNDS_LABEL,
                type = AppInputType.NUMERIC_STRING,
                imeAction = ImeAction.Next,
                nextFocusRequester = ouncesFocusRequester,
                maxLength = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(poundsFocusRequester),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            AppInput(
                formControl = controls.ounces,
                label = EntryScreenStrings.OUNCES_LABEL,
                type = AppInputType.NUMERIC_STRING,
                imeAction = ImeAction.Next,
                nextFocusRequester = inchesFocusRequester,
                maxLength = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(ouncesFocusRequester),
            )
        }
    }
    Spacer(modifier = Modifier.height(MeTheme.spacing.xs))
    AppInput(
        formControl = controls.inches,
        label = EntryScreenStrings.INCHES_LABEL,
        type = AppInputType.NUMERIC_STRING,
        imeAction = ImeAction.Done,
        onImeAction = onImeAction,
        maxLength = 2,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(inchesFocusRequester),
    )
    Spacer(modifier = Modifier.height(MeTheme.spacing.xs))
    AppTextArea(
        formControl = controls.notes,
        label = EntryScreenStrings.NOTES_LABEL,
    )
    Spacer(modifier = Modifier.height(MeTheme.spacing.md))
    DateTimeInput(
        formControl = controls.dateTime,
        mode = DateTimeInputMode.DateTime,
        label = EntryScreenStrings.DATE_LABEL,
        maxValue = null,
    )
}
