package com.dmdbrands.gurus.weight.features.manualEntry.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import com.dmdbrands.gurus.weight.features.common.components.AnimatedAppInput
import com.dmdbrands.gurus.weight.features.common.components.AppInputType
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.manualEntry.strings.EntryScreenStrings
import com.dmdbrands.gurus.weight.features.manualEntry.viewmodel.R4ScaleMetricsFormControls
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Composable section for R4/scale metrics (device-dependent metrics).
 *
 * @param controls The form controls for R4/scale metrics.
 * @param onImeAction Optional callback for when the last input's IME action is triggered.
 * @param heartRateFocusRequester Optional external focus requester for heart rate field.
 */
@Composable
fun R4ScaleMetricsSection(
    controls: R4ScaleMetricsFormControls,
    onImeAction: (() -> Unit)? = null,
    heartRateFocusRequester: FocusRequester? = null,
) {
    val internalHeartRateFocusRequester = remember { FocusRequester() }
    val boneMassFocusRequester = remember { FocusRequester() }
    val visceralFatFocusRequester = remember { FocusRequester() }
    val subcutaneousFatFocusRequester = remember { FocusRequester() }
    val proteinFocusRequester = remember { FocusRequester() }
    val skeletalMusclesFocusRequester = remember { FocusRequester() }
    val bmrFocusRequester = remember { FocusRequester() }
    val metabolicAgeFocusRequester = remember { FocusRequester() }

    // Use external focus requester if provided, otherwise use internal one
    val finalHeartRateFocusRequester = heartRateFocusRequester ?: internalHeartRateFocusRequester

    Column(modifier = Modifier.padding(top = MeTheme.spacing.x2s)) {
        AnimatedAppInput(
            formControl = controls.heartRate,
            label = EntryScreenStrings.HEART_RATE_LABEL,
            type = AppInputType.NUMBER,
            imeAction = ImeAction.Next,
            nextFocusRequester = boneMassFocusRequester,
            onImeAction = null,
            maxLength = 3,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(finalHeartRateFocusRequester),
            index = 0,
        )
        AnimatedAppInput(
            formControl = controls.boneMass,
            label = EntryScreenStrings.BONE_MASS_LABEL,
            type = AppInputType.BODY_COMP,
            imeAction = ImeAction.Next,
            nextFocusRequester = visceralFatFocusRequester,
            onImeAction = null,
            maxLength = 3,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(boneMassFocusRequester),
            index = 1,
        )
        AnimatedAppInput(
            formControl = controls.visceralFat,
            label = EntryScreenStrings.VISCERAL_FAT_LABEL,
            type = AppInputType.NUMBER,
            imeAction = ImeAction.Next,
            nextFocusRequester = subcutaneousFatFocusRequester,
            onImeAction = null,
            maxLength = 3,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(visceralFatFocusRequester),
            index = 2,
        )
        AnimatedAppInput(
            formControl = controls.subcutaneousFat,
            label = EntryScreenStrings.SUBCUTANEOUS_FAT_LABEL,
            type = AppInputType.BODY_COMP,
            imeAction = ImeAction.Next,
            nextFocusRequester = proteinFocusRequester,
            onImeAction = null,
            maxLength = 3,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(subcutaneousFatFocusRequester),
            index = 3,
        )
        AnimatedAppInput(
            formControl = controls.protein,
            label = EntryScreenStrings.PROTEIN_LABEL,
            type = AppInputType.BODY_COMP,
            imeAction = ImeAction.Next,
            nextFocusRequester = skeletalMusclesFocusRequester,
            onImeAction = null,
            maxLength = 3,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(proteinFocusRequester),
            index = 4,
        )
        AnimatedAppInput(
            formControl = controls.skeletalMuscles,
            label = EntryScreenStrings.SKELETAL_MUSCLES_LABEL,
            type = AppInputType.BODY_COMP,
            imeAction = ImeAction.Next,
            nextFocusRequester = bmrFocusRequester,
            onImeAction = null,
            maxLength = 3,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(skeletalMusclesFocusRequester),
            index = 5,
        )
        AnimatedAppInput(
            formControl = controls.bmr,
            label = EntryScreenStrings.BMR_LABEL,
            type = AppInputType.NUMBER,
            imeAction = ImeAction.Next,
            nextFocusRequester = metabolicAgeFocusRequester,
            onImeAction = null,
            maxLength = 5,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(bmrFocusRequester),
            index = 6,
        )
        AnimatedAppInput(
            formControl = controls.metabolicAge,
            label = EntryScreenStrings.METABOLIC_AGE_LABEL,
            type = AppInputType.NUMBER,
            imeAction = ImeAction.Done,
            nextFocusRequester = null,
            onImeAction = onImeAction,
            maxLength = 3,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(metabolicAgeFocusRequester),
            index = 7,
        )
    }
}

@Preview
@Composable
private fun R4ScaleMetricsSectionPreview() {
    MeAppTheme {
        val controls =
            R4ScaleMetricsFormControls(
                heartRate = FormControl.create("", emptyList()),
                boneMass = FormControl.create("", emptyList()),
                visceralFat = FormControl.create("", emptyList()),
                subcutaneousFat = FormControl.create("", emptyList()),
                protein = FormControl.create("", emptyList()),
                skeletalMuscles = FormControl.create("", emptyList()),
                bmr = FormControl.create("", emptyList()),
                metabolicAge = FormControl.create("", emptyList()),
            )
        R4ScaleMetricsSection(controls)
    }
}
