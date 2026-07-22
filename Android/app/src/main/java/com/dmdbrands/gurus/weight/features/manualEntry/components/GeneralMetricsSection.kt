package com.dmdbrands.gurus.weight.features.manualEntry.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.features.common.components.AppInputType
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.manualEntry.strings.EntryScreenStrings
import com.dmdbrands.gurus.weight.features.manualEntry.viewmodel.GeneralMetricsFormControls
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

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
    enabled: Boolean = true,
) {
    val bmiFocusRequester = remember { FocusRequester() }
    val bodyFatFocusRequester = remember { FocusRequester() }
    val muscleMassFocusRequester = remember { FocusRequester() }
    val bodyWaterFocusRequester = remember { FocusRequester() }
    val isLastSection = isDashboardType == DashboardType.DASHBOARD_4_METRICS

    Column(modifier = Modifier.padding(top = MeTheme.spacing.md)) {
        MetricInputField(
            formControl = controls.bodyMassIndex,
            label = EntryScreenStrings.BODY_MASS_INDEX_LABEL,
            type = AppInputType.BODY_COMP,
            imeAction = ImeAction.Next,
            focusRequester = bmiFocusRequester,
            nextFocusRequester = bodyFatFocusRequester,
            maxLength = 3,
            index = 0,
            testTag = "bmi_field",
            enabled = enabled,
        )
        MetricInputField(
            formControl = controls.bodyFat,
            label = EntryScreenStrings.BODY_FAT_LABEL,
            trailingText = EntryScreenStrings.BODY_FAT_UNIT,
            type = AppInputType.BODY_COMP,
            imeAction = ImeAction.Next,
            focusRequester = bodyFatFocusRequester,
            nextFocusRequester = muscleMassFocusRequester,
            maxLength = 3,
            index = 1,
            testTag = "body_fat_field",
            enabled = enabled,
        )
        GeneralMetricsTail(
            controls = controls,
            muscleMassFocusRequester = muscleMassFocusRequester,
            bodyWaterFocusRequester = bodyWaterFocusRequester,
            isLastSection = isLastSection,
            nextFocusRequester = nextFocusRequester,
            onImeAction = onImeAction,
            enabled = enabled,
        )
    }
}

/** Renders the last two general-metric fields (muscle mass → body water). */
@Composable
private fun GeneralMetricsTail(
    controls: GeneralMetricsFormControls,
    muscleMassFocusRequester: FocusRequester,
    bodyWaterFocusRequester: FocusRequester,
    isLastSection: Boolean,
    nextFocusRequester: FocusRequester?,
    onImeAction: (() -> Unit)?,
    enabled: Boolean,
) {
    MetricInputField(
        formControl = controls.muscleMass,
        label = EntryScreenStrings.MUSCLE_MASS_LABEL,
        trailingText = EntryScreenStrings.MUSCLE_MASS_UNIT,
        type = AppInputType.BODY_COMP,
        imeAction = ImeAction.Next,
        focusRequester = muscleMassFocusRequester,
        nextFocusRequester = bodyWaterFocusRequester,
        maxLength = 3,
        index = 2,
        testTag = "muscle_mass_field",
        enabled = enabled,
    )
    MetricInputField(
        formControl = controls.bodyWater,
        label = EntryScreenStrings.BODY_WATER_LABEL,
        trailingText = EntryScreenStrings.BODY_WATER_UNIT,
        type = AppInputType.BODY_COMP,
        imeAction = if (isLastSection) ImeAction.Done else ImeAction.Next,
        focusRequester = bodyWaterFocusRequester,
        nextFocusRequester = if (!isLastSection) nextFocusRequester else null,
        onImeAction = if (isLastSection) onImeAction else null,
        maxLength = 3,
        index = 3,
        testTag = "body_water_field",
        enabled = enabled,
    )
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
