package com.greatergoods.meapp.features.entry.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.greatergoods.meapp.features.common.components.AppInput
import com.greatergoods.meapp.features.common.components.AppInputType
import com.greatergoods.meapp.features.entry.strings.EntryScreenStrings
import com.greatergoods.meapp.features.entry.viewmodel.R4ScaleMetricsFormControls
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.greatergoods.meapp.features.common.helper.form.FormControl

/**
 * Composable section for R4/scale metrics (device-dependent metrics).
 *
 * @param controls The form controls for R4/scale metrics.
 */
@Composable
fun R4ScaleMetricsSection(controls: R4ScaleMetricsFormControls) {
    Column(modifier = Modifier.padding(top = MeTheme.spacing.md)) {
        AppInput(
            formControl = controls.heartRate,
            label = EntryScreenStrings.HEART_RATE_LABEL,
            type = AppInputType.NUMBER,
            modifier = Modifier.fillMaxWidth(),
        )
        AppInput(
            formControl = controls.boneMass,
            label = EntryScreenStrings.BONE_MASS_LABEL,
            type = AppInputType.NUMBER,
            modifier = Modifier.fillMaxWidth(),
        )
        AppInput(
            formControl = controls.visceralFat,
            label = EntryScreenStrings.VISCERAL_FAT_LABEL,
            type = AppInputType.NUMBER,
            modifier = Modifier.fillMaxWidth(),
        )
        AppInput(
            formControl = controls.subcutaneousFat,
            label = EntryScreenStrings.SUBCUTANEOUS_FAT_LABEL,
            type = AppInputType.NUMBER,
            modifier = Modifier.fillMaxWidth(),
        )
        AppInput(
            formControl = controls.protein,
            label = EntryScreenStrings.PROTEIN_LABEL,
            type = AppInputType.NUMBER,
            modifier = Modifier.fillMaxWidth(),
        )
        AppInput(
            formControl = controls.skeletalMuscles,
            label = EntryScreenStrings.SKELETAL_MUSCLES_LABEL,
            type = AppInputType.NUMBER,
            modifier = Modifier.fillMaxWidth(),
        )
        AppInput(
            formControl = controls.bmr,
            label = EntryScreenStrings.BMR_LABEL,
            type = AppInputType.NUMBER,
            modifier = Modifier.fillMaxWidth(),
        )
        AppInput(
            formControl = controls.metabolicAge,
            label = EntryScreenStrings.METABOLIC_AGE_LABEL,
            type = AppInputType.NUMBER,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview
@Composable
private fun R4ScaleMetricsSectionPreview() {
    MeAppTheme {
        val fakeScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        val controls = R4ScaleMetricsFormControls(
            heartRate = FormControl("", emptyList(), emptyList(), fakeScope),
            boneMass = FormControl("", emptyList(), emptyList(), fakeScope),
            visceralFat = FormControl("", emptyList(), emptyList(), fakeScope),
            subcutaneousFat = FormControl("", emptyList(), emptyList(), fakeScope),
            protein = FormControl("", emptyList(), emptyList(), fakeScope),
            skeletalMuscles = FormControl("", emptyList(), emptyList(), fakeScope),
            bmr = FormControl("", emptyList(), emptyList(), fakeScope),
            metabolicAge = FormControl("", emptyList(), emptyList(), fakeScope),
        )
        R4ScaleMetricsSection(controls)
    }
}
