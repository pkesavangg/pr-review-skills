package com.dmdbrands.gurus.weight.features.manualEntry.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
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
    enabled: Boolean = true,
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
        R4MetricsGroupA(
            controls = controls,
            heartRateFocusRequester = finalHeartRateFocusRequester,
            boneMassFocusRequester = boneMassFocusRequester,
            visceralFatFocusRequester = visceralFatFocusRequester,
            subcutaneousFatFocusRequester = subcutaneousFatFocusRequester,
            proteinFocusRequester = proteinFocusRequester,
            enabled = enabled,
        )
        R4MetricsGroupB(
            controls = controls,
            proteinFocusRequester = proteinFocusRequester,
            skeletalMusclesFocusRequester = skeletalMusclesFocusRequester,
            bmrFocusRequester = bmrFocusRequester,
            metabolicAgeFocusRequester = metabolicAgeFocusRequester,
            onImeAction = onImeAction,
            enabled = enabled,
        )
    }
}

/** Renders the first four R4 metric fields (heart rate → subcutaneous fat). */
@Composable
private fun R4MetricsGroupA(
    controls: R4ScaleMetricsFormControls,
    heartRateFocusRequester: FocusRequester,
    boneMassFocusRequester: FocusRequester,
    visceralFatFocusRequester: FocusRequester,
    subcutaneousFatFocusRequester: FocusRequester,
    proteinFocusRequester: FocusRequester,
    enabled: Boolean,
) {
    MetricInputField(
        formControl = controls.heartRate,
        label = EntryScreenStrings.HEART_RATE_LABEL,
        trailingText = EntryScreenStrings.HEART_RATE_UNIT,
        type = AppInputType.NUMBER,
        imeAction = ImeAction.Next,
        focusRequester = heartRateFocusRequester,
        nextFocusRequester = boneMassFocusRequester,
        onImeAction = null,
        maxLength = 3,
        index = 0,
        testTag = "heart_rate_field",
        enabled = enabled,
    )
    MetricInputField(
        formControl = controls.boneMass,
        label = EntryScreenStrings.BONE_MASS_LABEL,
        trailingText = EntryScreenStrings.BONE_MASS_UNIT,
        type = AppInputType.BODY_COMP,
        imeAction = ImeAction.Next,
        focusRequester = boneMassFocusRequester,
        nextFocusRequester = visceralFatFocusRequester,
        onImeAction = null,
        maxLength = 3,
        index = 1,
        testTag = "bone_mass_field",
        enabled = enabled,
    )
    MetricInputField(
        formControl = controls.visceralFat,
        label = EntryScreenStrings.VISCERAL_FAT_LABEL,
        trailingText = EntryScreenStrings.VISCERAL_FAT_UNIT,
        type = AppInputType.NUMBER,
        imeAction = ImeAction.Next,
        focusRequester = visceralFatFocusRequester,
        nextFocusRequester = subcutaneousFatFocusRequester,
        onImeAction = null,
        maxLength = 3,
        index = 2,
        testTag = "visceral_fat_field",
        enabled = enabled,
    )
    MetricInputField(
        formControl = controls.subcutaneousFat,
        label = EntryScreenStrings.SUBCUTANEOUS_FAT_LABEL,
        trailingText = EntryScreenStrings.SUBCUTANEOUS_FAT_UNIT,
        type = AppInputType.BODY_COMP,
        imeAction = ImeAction.Next,
        focusRequester = subcutaneousFatFocusRequester,
        nextFocusRequester = proteinFocusRequester,
        onImeAction = null,
        maxLength = 3,
        index = 3,
        testTag = "subcutaneous_fat_field",
        enabled = enabled,
    )
}

/** Renders the last four R4 metric fields (protein → metabolic age); metabolic age closes the IME. */
@Composable
private fun R4MetricsGroupB(
    controls: R4ScaleMetricsFormControls,
    proteinFocusRequester: FocusRequester,
    skeletalMusclesFocusRequester: FocusRequester,
    bmrFocusRequester: FocusRequester,
    metabolicAgeFocusRequester: FocusRequester,
    onImeAction: (() -> Unit)?,
    enabled: Boolean,
) {
    MetricInputField(
        formControl = controls.protein,
        label = EntryScreenStrings.PROTEIN_LABEL,
        trailingText = EntryScreenStrings.PROTEIN_UNIT,
        type = AppInputType.BODY_COMP,
        imeAction = ImeAction.Next,
        focusRequester = proteinFocusRequester,
        nextFocusRequester = skeletalMusclesFocusRequester,
        onImeAction = null,
        maxLength = 3,
        index = 4,
        testTag = "protein_field",
        enabled = enabled,
    )
    MetricInputField(
        formControl = controls.skeletalMuscles,
        label = EntryScreenStrings.SKELETAL_MUSCLES_LABEL,
        trailingText = EntryScreenStrings.SKELETAL_MUSCLES_UNIT,
        type = AppInputType.BODY_COMP,
        imeAction = ImeAction.Next,
        focusRequester = skeletalMusclesFocusRequester,
        nextFocusRequester = bmrFocusRequester,
        onImeAction = null,
        maxLength = 3,
        index = 5,
        testTag = "skeletal_muscles_field",
        enabled = enabled,
    )
    MetricInputField(
        formControl = controls.bmr,
        label = EntryScreenStrings.BMR_LABEL,
        trailingText = EntryScreenStrings.BMR_UNIT,
        type = AppInputType.NUMBER,
        imeAction = ImeAction.Next,
        focusRequester = bmrFocusRequester,
        nextFocusRequester = metabolicAgeFocusRequester,
        onImeAction = null,
        maxLength = 5,
        index = 6,
        testTag = "basal_metabolic_field",
        enabled = enabled,
    )
    MetricInputField(
        formControl = controls.metabolicAge,
        label = EntryScreenStrings.METABOLIC_AGE_LABEL,
        trailingText = EntryScreenStrings.METABOLIC_AGE_UNIT,
        type = AppInputType.NUMBER,
        imeAction = ImeAction.Done,
        focusRequester = metabolicAgeFocusRequester,
        nextFocusRequester = null,
        onImeAction = onImeAction,
        maxLength = 3,
        index = 7,
        testTag = "metabolic_age_field",
        enabled = enabled,
    )
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
