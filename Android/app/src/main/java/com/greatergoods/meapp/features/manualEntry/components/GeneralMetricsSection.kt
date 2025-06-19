package com.greatergoods.meapp.features.manualEntry.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.greatergoods.meapp.features.common.components.AppInput
import com.greatergoods.meapp.features.common.components.AppInputType
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.features.manualEntry.strings.EntryScreenStrings
import com.greatergoods.meapp.features.manualEntry.viewmodel.GeneralMetricsFormControls
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

/**
 * Composable section for general metrics (BMI, Body Fat, Muscle Mass, Body Water).
 *
 * @param controls The form controls for general metrics.
 */
@Composable
fun GeneralMetricsSection(controls: GeneralMetricsFormControls) {
    Column(modifier = Modifier.padding(top = MeTheme.spacing.md)) {
        AppInput(
            formControl = controls.bodyMassIndex,
            label = EntryScreenStrings.BODY_MASS_INDEX_LABEL,
            type = AppInputType.NUMBER,
            modifier = Modifier.fillMaxWidth(),
        )
        AppInput(
            formControl = controls.bodyFat,
            label = EntryScreenStrings.BODY_FAT_LABEL,
            type = AppInputType.NUMBER,
            modifier = Modifier.fillMaxWidth(),
        )
        AppInput(
            formControl = controls.muscleMass,
            label = EntryScreenStrings.MUSCLE_MASS_LABEL,
            type = AppInputType.NUMBER,
            modifier = Modifier.fillMaxWidth(),
        )
        AppInput(
            formControl = controls.bodyWater,
            label = EntryScreenStrings.BODY_WATER_LABEL,
            type = AppInputType.NUMBER,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview
@Composable
private fun GeneralMetricsSectionPreview() {
    MeAppTheme {
        val controls =
            GeneralMetricsFormControls(
                bodyMassIndex = FormControl.create("", emptyList()),
                bodyFat = FormControl.create("", emptyList()),
                muscleMass = FormControl.create("", emptyList()),
                bodyWater = FormControl.create("", emptyList()),
            )
        GeneralMetricsSection(controls)
    }
}
