package com.greatergoods.meapp.features.manualEntry.viewmodel

import com.greatergoods.meapp.domain.interfaces.IReducer
import com.greatergoods.meapp.domain.model.common.WeightUnit
import com.greatergoods.meapp.features.common.components.DateTimeValue
import com.greatergoods.meapp.features.common.helper.form.AppValidatorConfig
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.features.common.helper.form.FormGroup
import com.greatergoods.meapp.features.common.helper.form.FormValidations
import kotlinx.coroutines.CoroutineScope
import java.util.Calendar

/**
 * Form controls for weight and date/time (always present)
 */
data class WeightDateTimeFormControls(
    val weight: FormControl<String>,
    val dateTime: FormControl<DateTimeValue>,
)

/**
 * Form controls for general metrics (optional section, e.g., height)
 */
data class GeneralMetricsFormControls(
    val bodyMassIndex: FormControl<String>, // BMI
    val bodyFat: FormControl<String>,
    val muscleMass: FormControl<String>,
    val bodyWater: FormControl<String>,
)

/**
 * R4/scale/device-dependent metrics (optional section)
 */
data class R4ScaleMetricsFormControls(
    val heartRate: FormControl<String>,
    val boneMass: FormControl<String>,
    val visceralFat: FormControl<String>,
    val subcutaneousFat: FormControl<String>,
    val protein: FormControl<String>,
    val skeletalMuscles: FormControl<String>,
    val bmr: FormControl<String>, // Basel Metabolic Rate
    val metabolicAge: FormControl<String>,
)

/**
 * Main entry form controls, composed of the three groups above
 */
data class EntryFormControls(
    val weightDateTime: WeightDateTimeFormControls,
    val generalMetrics: GeneralMetricsFormControls,
    val r4ScaleMetrics: R4ScaleMetricsFormControls? = null,
) {

    companion object {
        val calendar = Calendar.getInstance()
        val currentTimeMillis = calendar.timeInMillis
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        fun create(
            scope: CoroutineScope,
            includeR4ScaleMetrics: Boolean = false,
            weightMode: WeightUnit = WeightUnit.LB,
        ): EntryFormControls =
            EntryFormControls(
                weightDateTime =
                    WeightDateTimeFormControls(
                        weight =
                            FormControl.create(
                                initialValue = "",
                                validators = listOf(
                                    FormValidations.required(),
                                    FormValidations.weightValidator(weightMode),
                                ),
                            ),
                        dateTime =
                            FormControl.create(
                                initialValue = DateTimeValue.DateTime(currentTimeMillis, hour, minute),
                                validators = emptyList(),
                            ),
                    ),
                generalMetrics =
                    GeneralMetricsFormControls(
                        bodyMassIndex = FormControl.create("", listOf(FormValidations.bodyCompValidator())),
                        bodyFat = FormControl.create("", listOf(FormValidations.bodyCompValidator())),
                        muscleMass = FormControl.create("", listOf(FormValidations.bodyCompValidator())),
                        bodyWater = FormControl.create("", listOf(FormValidations.bodyCompValidator())),
                        // Add more general metrics here if needed
                    ),
                r4ScaleMetrics =
                    if (includeR4ScaleMetrics) {
                        R4ScaleMetricsFormControls(
                            heartRate = FormControl.create(
                                "",
                                listOf(
                                    FormValidations.bodyCompValidator(
                                        AppValidatorConfig.BodyComp.MIN, AppValidatorConfig.BodyComp.MAX, false,
                                    ),
                                ),
                            ),
                            boneMass = FormControl.create("", listOf(FormValidations.bodyCompValidator())),
                            visceralFat = FormControl.create("", listOf(FormValidations.bodyCompValidator())),
                            subcutaneousFat = FormControl.create("", listOf(FormValidations.bodyCompValidator())),
                            protein = FormControl.create("", listOf(FormValidations.bodyCompValidator())),
                            skeletalMuscles = FormControl.create("", listOf(FormValidations.bodyCompValidator())),
                            bmr = FormControl.create(
                                "",
                                listOf(
                                    FormValidations.bodyCompValidator(
                                        AppValidatorConfig.BMR.MIN, AppValidatorConfig.BMR.MAX, false,
                                    ),
                                ),
                            ),
                            metabolicAge = FormControl.create(
                                "",
                                listOf(
                                    FormValidations.bodyCompValidator(
                                        AppValidatorConfig.MetabolicAge.MIN, AppValidatorConfig.MetabolicAge.MAX, false,
                                    ),
                                ),
                            ),
                        )
                    } else {
                        null
                    },
            )
    }
}

data class EntryState(
    val form: FormGroup<EntryFormControls>,
    val weightMode: WeightUnit = WeightUnit.LB,
    val isLoading: Boolean = false,
) : IReducer.State

/**
 * Intent for entry actions, such as loading, selecting, adding, and deleting entries.
 */
sealed interface EntryIntent : IReducer.Intent {
    data object Save : EntryIntent
}

/**
 * Reducer for the entry state, handling intents to update months, entries, and errors.
 */
class EntryReducer : IReducer<EntryState, EntryIntent> {
    override fun reduce(
        state: EntryState,
        intent: EntryIntent,
    ): EntryState? = state
}
