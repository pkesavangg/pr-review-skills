package com.greatergoods.meapp.features.manualEntry.viewmodel

import com.greatergoods.meapp.domain.interfaces.IReducer
import com.greatergoods.meapp.features.common.components.DateTimeValue
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
        ): EntryFormControls =
            EntryFormControls(
                weightDateTime =
                    WeightDateTimeFormControls(
                        weight =
                            FormControl.create(
                                initialValue = "",
                                validators = listOf(FormValidations.required()),
                            ),
                        dateTime =
                            FormControl.create(
                                initialValue = DateTimeValue.DateTime(currentTimeMillis, hour, minute),
                                validators = emptyList(),
                            ),
                    ),
                generalMetrics =
                    GeneralMetricsFormControls(
                        bodyMassIndex = FormControl.create("", emptyList()),
                        bodyFat = FormControl.create("", emptyList()),
                        muscleMass = FormControl.create("", emptyList()),
                        bodyWater = FormControl.create("", emptyList()),
                        // Add more general metrics here if needed
                    ),
                r4ScaleMetrics =
                    if (includeR4ScaleMetrics) {
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
                    } else {
                        null
                    },
            )
    }
}

data class EntryState(
    val form: FormGroup<EntryFormControls>,
    val weightMode: String = "lbs",
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
