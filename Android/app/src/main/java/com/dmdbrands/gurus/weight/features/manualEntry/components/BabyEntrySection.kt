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
import com.dmdbrands.gurus.weight.core.shared.utilities.testing.TestTags
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.features.common.components.AppInput
import com.dmdbrands.gurus.weight.features.common.components.AppInputType
import com.dmdbrands.gurus.weight.features.common.components.AppTextArea
import com.dmdbrands.gurus.weight.features.common.components.DateTimeInput
import com.dmdbrands.gurus.weight.features.common.components.DateTimeInputMode
import com.dmdbrands.gurus.weight.features.manualEntry.strings.EntryScreenStrings
import com.dmdbrands.gurus.weight.features.manualEntry.viewmodel.BabyEntryFormControls
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Baby entry section (MOB-1223). The weight/length fields follow the account's Unit Type — there
 * is no on-screen toggle — mirroring the Add-a-Baby / Weight-Scale / BPM field-label pattern:
 *  - lb/oz → two weight fields (weight (lb) + ounces (oz)), length (in)
 *  - lb    → one weight field (weight (lb)), length (in)
 *  - kg    → one weight field (weight (kg)), length (cm)
 *
 * Labels name the metric ("weight" / "ounces" / "length"); the unit is the right-edge "(unit)"
 * suffix (AppInput.trailingText). Notes + date/time are unchanged across units.
 */
@Suppress("LongMethod")
@Composable
fun BabyEntrySection(
    controls: BabyEntryFormControls,
    weightUnit: WeightUnit,
    onImeAction: () -> Unit,
    enabled: Boolean = true,
) {
    val isMetric = weightUnit == WeightUnit.KG
    val isLbOz = weightUnit == WeightUnit.LB_OZ

    val weightFocusRequester = remember { FocusRequester() }
    val weightOzFocusRequester = remember { FocusRequester() }
    val lengthFocusRequester = remember { FocusRequester() }

    BabyWeightInput(
        controls = controls,
        isMetric = isMetric,
        isLbOz = isLbOz,
        weightFocusRequester = weightFocusRequester,
        weightOzFocusRequester = weightOzFocusRequester,
        lengthFocusRequester = lengthFocusRequester,
        enabled = enabled,
    )
    Spacer(modifier = Modifier.height(MeTheme.spacing.xs))
    AppInput(
        formControl = controls.length,
        label = EntryScreenStrings.LENGTH_LABEL,
        trailingText = if (isMetric) EntryScreenStrings.BABY_LENGTH_CM_UNIT else EntryScreenStrings.BABY_LENGTH_IN_UNIT,
        // Implicit 1-decimal like ounces (BODY_COMP): type 0 → 0.0, "205" → 20.5. No '.' key
        // needed (sidesteps OEM keyboards that hide it). Value stored as raw digits. (MOB-1223)
        type = AppInputType.BODY_COMP,
        imeAction = ImeAction.Done,
        onImeAction = onImeAction,
        maxLength = if (isMetric) 4 else 3,
        testTag = TestTags.ManualEntry.BabyLengthField,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(lengthFocusRequester),
    )
    Spacer(modifier = Modifier.height(MeTheme.spacing.xs))
    // Note stays editable for both manual and device-synced readings.
    AppTextArea(
        formControl = controls.notes,
        label = EntryScreenStrings.NOTES_LABEL,
        maxLength = EntryScreenStrings.NOTES_MAX_LENGTH,
        showCharacterCounter = true,
    )
    Spacer(modifier = Modifier.height(MeTheme.spacing.md))
    DateTimeInput(
        formControl = controls.dateTime,
        mode = DateTimeInputMode.DateTime,
        label = EntryScreenStrings.DATE_LABEL,
        maxValue = null,
        enabled = enabled,
    )
}

/**
 * The weight input(s) for the account's unit: two fields (weight (lb) + ounces (oz)) for lb/oz,
 * otherwise a single decimal weight field (kg or lb). Ounces uses the adult weight input
 * (BODY_COMP) — a number keypad with an implicit 1-place decimal (type "45" → 4.5), so no '.'
 * key is needed on any OEM keyboard; its value is stored as raw digits. (MOB-1223)
 */
@Composable
private fun BabyWeightInput(
    controls: BabyEntryFormControls,
    isMetric: Boolean,
    isLbOz: Boolean,
    weightFocusRequester: FocusRequester,
    weightOzFocusRequester: FocusRequester,
    lengthFocusRequester: FocusRequester,
    enabled: Boolean = true,
) {
    if (isLbOz) {
        // Each AppInput is wrapped in a Column so its error text + spacer stay within its half
        // of the Row (InputFieldBase emits multiple composables).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MeTheme.spacing.md),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                AppInput(
                    formControl = controls.weight,
                    label = EntryScreenStrings.WEIGHT_LABEL,
                    trailingText = EntryScreenStrings.BABY_WEIGHT_LB_UNIT,
                    type = AppInputType.NUMERIC_STRING,
                    imeAction = ImeAction.Next,
                    nextFocusRequester = weightOzFocusRequester,
                    maxLength = 3,
                    testTag = TestTags.ManualEntry.BabyWeightField,
                    enabled = enabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(weightFocusRequester),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                AppInput(
                    formControl = controls.weightOz,
                    label = EntryScreenStrings.OUNCES_LABEL,
                    trailingText = EntryScreenStrings.BABY_WEIGHT_OZ_UNIT,
                    type = AppInputType.BODY_COMP,
                    imeAction = ImeAction.Next,
                    nextFocusRequester = lengthFocusRequester,
                    maxLength = 3,
                    enabled = enabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(weightOzFocusRequester),
                )
            }
        }
    } else {
        // Single decimal weight field: kg (metric) or lb (imperial decimal).
        AppInput(
            formControl = controls.weight,
            label = EntryScreenStrings.WEIGHT_LABEL,
            trailingText = if (isMetric) EntryScreenStrings.BABY_WEIGHT_KG_UNIT else EntryScreenStrings.BABY_WEIGHT_LB_UNIT,
            type = AppInputType.DECIMAL_STRING,
            imeAction = ImeAction.Next,
            nextFocusRequester = lengthFocusRequester,
            maxLength = 5,
            testTag = TestTags.ManualEntry.BabyWeightField,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(weightFocusRequester),
        )
    }
}
