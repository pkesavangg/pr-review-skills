package com.greatergoods.meapp.features.manualEntry.components

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
import com.greatergoods.meapp.features.common.components.AppInput
import com.greatergoods.meapp.features.common.components.AppInputType
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.features.manualEntry.strings.EntryScreenStrings
import com.greatergoods.meapp.features.manualEntry.viewmodel.R4ScaleMetricsFormControls
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

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
        AppInput(
            formControl = controls.heartRate,
            label = EntryScreenStrings.HEART_RATE_LABEL,
            type = AppInputType.NUMBER,
            imeAction = ImeAction.Next,
            nextFocusRequester = boneMassFocusRequester,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(finalHeartRateFocusRequester),
        )
        AppInput(
            formControl = controls.boneMass,
            label = EntryScreenStrings.BONE_MASS_LABEL,
            type = AppInputType.BODY_COMP,
            imeAction = ImeAction.Next,
            nextFocusRequester = visceralFatFocusRequester,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(boneMassFocusRequester),
        )
        AppInput(
            formControl = controls.visceralFat,
            label = EntryScreenStrings.VISCERAL_FAT_LABEL,
            type = AppInputType.BODY_COMP,
            imeAction = ImeAction.Next,
            nextFocusRequester = subcutaneousFatFocusRequester,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(visceralFatFocusRequester),
        )
        AppInput(
            formControl = controls.subcutaneousFat,
            label = EntryScreenStrings.SUBCUTANEOUS_FAT_LABEL,
            type = AppInputType.BODY_COMP,
            imeAction = ImeAction.Next,
            nextFocusRequester = proteinFocusRequester,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(subcutaneousFatFocusRequester),
        )
        AppInput(
            formControl = controls.protein,
            label = EntryScreenStrings.PROTEIN_LABEL,
            type = AppInputType.BODY_COMP,
            imeAction = ImeAction.Next,
            nextFocusRequester = skeletalMusclesFocusRequester,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(proteinFocusRequester),
        )
        AppInput(
            formControl = controls.skeletalMuscles,
            label = EntryScreenStrings.SKELETAL_MUSCLES_LABEL,
            type = AppInputType.BODY_COMP,
            imeAction = ImeAction.Next,
            nextFocusRequester = bmrFocusRequester,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(skeletalMusclesFocusRequester),
        )
        AppInput(
            formControl = controls.bmr,
            label = EntryScreenStrings.BMR_LABEL,
            type = AppInputType.NUMBER,
            imeAction = ImeAction.Next,
            nextFocusRequester = metabolicAgeFocusRequester,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(bmrFocusRequester),
        )
        AppInput(
            formControl = controls.metabolicAge,
            label = EntryScreenStrings.METABOLIC_AGE_LABEL,
            type = AppInputType.NUMBER,
            imeAction = ImeAction.Done,
            onImeAction = onImeAction,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(metabolicAgeFocusRequester),
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
