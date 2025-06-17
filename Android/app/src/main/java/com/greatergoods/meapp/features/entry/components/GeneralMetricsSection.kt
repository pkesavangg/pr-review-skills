package com.greatergoods.meapp.features.entry.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.features.common.components.AppInput
import com.greatergoods.meapp.features.common.components.AppInputType
import com.greatergoods.meapp.features.entry.strings.EntryScreenStrings
import com.greatergoods.meapp.features.entry.viewmodel.GeneralMetricsFormControls
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.greatergoods.meapp.features.common.helper.form.FormControl

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
        val fakeScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        val controls = GeneralMetricsFormControls(
            bodyMassIndex = FormControl("", emptyList(), emptyList(), fakeScope),
            bodyFat = FormControl("", emptyList(), emptyList(), fakeScope),
            muscleMass = FormControl("", emptyList(), emptyList(), fakeScope),
            bodyWater = FormControl("", emptyList(), emptyList(), fakeScope),
        )
        GeneralMetricsSection(controls)
    }
}
