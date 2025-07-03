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
import com.greatergoods.meapp.domain.enums.DashboardType
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
 * @param isDashboardType The dashboard type to determine if this is the last section.
 * @param nextFocusRequester Optional focus requester for the next field (used when not the last section).
 * @param onImeAction Optional callback for when the last input's IME action is triggered.
 */
@Composable
fun GeneralMetricsSection(
    controls: GeneralMetricsFormControls,
    isDashboardType: DashboardType,
    nextFocusRequester: FocusRequester? = null,
    onImeAction: (() -> Unit)? = null,
) {
    val bmiFocusRequester = remember { FocusRequester() }
    val bodyFatFocusRequester = remember { FocusRequester() }
    val muscleMassFocusRequester = remember { FocusRequester() }
    val bodyWaterFocusRequester = remember { FocusRequester() }
    val isLastSection = isDashboardType == DashboardType.DASHBOARD_4_METRICS

    Column(modifier = Modifier.padding(top = MeTheme.spacing.md)) {
        AppInput(
            formControl = controls.bodyMassIndex,
            label = EntryScreenStrings.BODY_MASS_INDEX_LABEL,
            type = AppInputType.BODY_COMP,
            imeAction = ImeAction.Next,
            nextFocusRequester = bodyFatFocusRequester,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(bmiFocusRequester),
        )
        AppInput(
            formControl = controls.bodyFat,
            label = EntryScreenStrings.BODY_FAT_LABEL,
            type = AppInputType.BODY_COMP,
            imeAction = ImeAction.Next,
            nextFocusRequester = muscleMassFocusRequester,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(bodyFatFocusRequester),
        )
        AppInput(
            formControl = controls.muscleMass,
            label = EntryScreenStrings.MUSCLE_MASS_LABEL,
            type = AppInputType.BODY_COMP,
            imeAction = ImeAction.Next,
            nextFocusRequester = bodyWaterFocusRequester,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(muscleMassFocusRequester),
        )
        AppInput(
            formControl = controls.bodyWater,
            label = EntryScreenStrings.BODY_WATER_LABEL,
            type = AppInputType.BODY_COMP,
            imeAction = if (isLastSection) ImeAction.Done else ImeAction.Next,
            nextFocusRequester = if (!isLastSection) nextFocusRequester else null,
            onImeAction = if (isLastSection) onImeAction else null,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(bodyWaterFocusRequester),
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
        GeneralMetricsSection(
            controls,
            DashboardType.DASHBOARD_12_METRICS,
            onImeAction = {},
        )
    }
}
