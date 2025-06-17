package com.greatergoods.meapp.features.entry.viewmodel

import com.greatergoods.meapp.domain.interfaces.IReducer
import com.greatergoods.meapp.features.common.components.DateTimeValue
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.features.common.helper.form.FormGroup
import com.greatergoods.meapp.features.common.helper.form.FormValidations
import kotlinx.coroutines.CoroutineScope

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
    val bodyWater: FormControl<String>
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
    val metabolicAge: FormControl<String>
)

/**
 * Main entry form controls, composed of the three groups above
 */
data class EntryFormControls(
    val weightDateTime: WeightDateTimeFormControls,
    val generalMetrics: GeneralMetricsFormControls,
    val r4ScaleMetrics: R4ScaleMetricsFormControls? = null
) {
    companion object {
        fun create(
            scope: CoroutineScope,
            includeR4ScaleMetrics: Boolean = false
        ): EntryFormControls = EntryFormControls(
            weightDateTime = WeightDateTimeFormControls(
                weight = FormControl(
                    initialValue = "",
                    validators = listOf(FormValidations.required()),
                    asyncValidators = emptyList(),
                    scope = scope,
                ),
                dateTime = FormControl(
                    initialValue = DateTimeValue.DateTime(System.currentTimeMillis(), 12, 0),
                    validators = emptyList(),
                    asyncValidators = emptyList(),
                    scope = scope,
                ),
            ),
            generalMetrics = GeneralMetricsFormControls(
                bodyMassIndex = FormControl("", emptyList(), emptyList(), scope),
                bodyFat = FormControl("", emptyList(), emptyList(), scope),
                muscleMass = FormControl("", emptyList(), emptyList(), scope),
                bodyWater = FormControl("", emptyList(), emptyList(), scope),
                // Add more general metrics here if needed
            ),
            r4ScaleMetrics = if (includeR4ScaleMetrics) R4ScaleMetricsFormControls(
                heartRate = FormControl("", emptyList(), emptyList(), scope),
                boneMass = FormControl("", emptyList(), emptyList(), scope),
                visceralFat = FormControl("", emptyList(), emptyList(), scope),
                subcutaneousFat = FormControl("", emptyList(), emptyList(), scope),
                protein = FormControl("", emptyList(), emptyList(), scope),
                skeletalMuscles = FormControl("", emptyList(), emptyList(), scope),
                bmr = FormControl("", emptyList(), emptyList(), scope),
                metabolicAge = FormControl("", emptyList(), emptyList(), scope),

                ) else null,
        )
    }
}

data class EntryState(
    val form: FormGroup<EntryFormControls>,
    val isLoading: Boolean = false,
) : IReducer.State

/**
 * Intent for entry actions, such as loading, selecting, adding, and deleting entries.
 */
sealed interface EntryIntent : IReducer.Intent

/**
 * Reducer for the entry state, handling intents to update months, entries, and errors.
 */
class EntryReducer : IReducer<EntryState, EntryIntent> {
    override fun reduce(state: EntryState, intent: EntryIntent): EntryState? = state
}
