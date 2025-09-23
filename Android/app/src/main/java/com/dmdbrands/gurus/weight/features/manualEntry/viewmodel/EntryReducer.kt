package com.dmdbrands.gurus.weight.features.manualEntry.viewmodel

import com.dmdbrands.gurus.weight.core.shared.utilities.ConversionTools
import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.features.common.components.DateTimeValue
import com.dmdbrands.gurus.weight.features.common.helper.form.AppValidatorConfig
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup
import com.dmdbrands.gurus.weight.features.common.helper.form.FormValidations
import com.dmdbrands.gurus.weight.features.common.helper.form.MultiFormGroup
import java.time.LocalTime
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
data class EntryForm(
  val weightDateTime: FormGroup<WeightDateTimeFormControls>,
  val generalMetrics: FormGroup<GeneralMetricsFormControls>,
  val r4ScaleMetrics: FormGroup<R4ScaleMetricsFormControls>? = null,
) {

  companion object {
    val calendar = Calendar.getInstance()
    val currentTimeMillis = calendar.timeInMillis
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    val minute = calendar.get(Calendar.MINUTE)
    fun create(
      includeR4ScaleMetrics: Boolean = false,
      weightUnit: WeightUnit? = null,
      height: Int? = 0,
      scaleEntry: com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry? = null,
    ): EntryForm {
      val generalMetrics =
        GeneralMetricsFormControls(
          bodyMassIndex = FormControl.create(
            scaleEntry?.scale?.scaleEntry?.bmi?.times(10)?.toInt()?.toString() ?: "",
            listOf(FormValidations.bodyCompValidator()),
          ),
          bodyFat = FormControl.create(
            (scaleEntry?.scale?.scaleEntry?.bodyFat?.times(10)?.toInt()?.toString()) ?: "",
            listOf(FormValidations.bodyCompValidator()),
          ),
          muscleMass = FormControl.create(
            (scaleEntry?.scale?.scaleEntry?.muscleMass?.times(10)?.toInt()?.toString()) ?: "",
            listOf(FormValidations.bodyCompValidator()),
          ),
          bodyWater = FormControl.create(
            (scaleEntry?.scale?.scaleEntry?.water?.times(10)?.toInt()?.toString()) ?: "",
            listOf(FormValidations.bodyCompValidator()),
          ),
          // Add more general metrics here if needed
        )
      val weightDateTime =
        WeightDateTimeFormControls(
          weight = FormControl.create(
            if (scaleEntry != null) {
              val isMetric = scaleEntry.entry.unit.value.lowercase() == "kg"
              val displayWeight = ConversionTools.convertStoredToDisplay(scaleEntry.scale.scaleEntry.weight, isMetric)
              displayWeight.times(10).toInt().toString()
            } else "",
            listOf(
              FormValidations.weightValidator(weightUnit),
              FormValidations.required(),
            ),
            onValueChangeCallback = { _, new ->
              if (height != null) {
                val weight = when {
                  new.isBlank() -> 0.0
                  weightUnit == WeightUnit.LB -> ConversionTools.convertStoredToKg(
                    ConversionTools.convertDisplayToStored(
                      new.toDouble() / 10,
                      forceMetric = false,
                      isMetric = false
                    ),
                  )

                  else -> new.toDouble() / 10
                }
                val storedHeight = ConversionTools.convertStoredHeightToCm(height)
                val bmi = ConversionTools.calculateBMI(weight, storedHeight)
                val bmiValue = when {
                  bmi <= 0.0 -> ""
                  bmi >= AppValidatorConfig.BodyComp.MAX * 10 -> AppValidatorConfig.BodyComp.MAX.times(
                    10,
                  ).toString()

                  else -> bmi.toString()
                }
                generalMetrics.bodyMassIndex.onValueChange(bmiValue)
              }
            },
          ),
          dateTime = FormControl.create(
            if (scaleEntry != null) {
              DateTimeValue.DateTime(
                millis = System.currentTimeMillis(),
                hour = LocalTime.now().hour,
                minute = LocalTime.now().minute,
              )
            } else {
              DateTimeValue.DateTime(millis = currentTimeMillis, hour = hour, minute = minute)
            },
            listOf(),
          ),
        )
      val r4ScaleMetrics =
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
        } else null
      return EntryForm(
        weightDateTime = FormGroup(weightDateTime),
        generalMetrics = FormGroup(generalMetrics),
        r4ScaleMetrics = if (r4ScaleMetrics != null) FormGroup(r4ScaleMetrics) else null,
      )
    }
  }
}

data class EntryState(
  val form: MultiFormGroup<EntryForm>,
  val weightMode: WeightUnit = WeightUnit.LB,
  val isLoading: Boolean = false,
) : IReducer.State

/**
 * Intent for entry actions, such as loading, selecting, adding, and deleting entries.
 */
sealed interface EntryIntent : IReducer.Intent {
  data object Save : EntryIntent
  data class UpdateForm(
    val form: MultiFormGroup<EntryForm>,
  ) : EntryIntent

  data class UpdateWeightUnit(val weightUnit: WeightUnit) : EntryIntent

  data class LoadAppSyncData(
    val scaleEntry: com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry,
    val height: Int? = null
  ) : EntryIntent
}

/**
 * Reducer for the entry state, handling intents to update months, entries, and errors.
 */
class EntryReducer : IReducer<EntryState, EntryIntent> {
  override fun reduce(
    state: EntryState,
    intent: EntryIntent,
  ): EntryState? {
    return when (intent) {
      is EntryIntent.UpdateForm -> {
        state.copy(
          form = intent.form,
        )
      }

      is EntryIntent.UpdateWeightUnit -> {
        state.copy(
          weightMode = intent.weightUnit,
        )
      }

      is EntryIntent.LoadAppSyncData -> {
        // Create new form with AppSync data, following Profile pattern exactly
        val scaleEntry = intent.scaleEntry
        val currentForm = state.form.forms
        val updatedForm = EntryForm.create(
          includeR4ScaleMetrics = currentForm.r4ScaleMetrics != null,
          weightUnit = state.weightMode,
          height = intent.height, // Use height from intent
          scaleEntry = scaleEntry,
        )

        state.copy(
          form = MultiFormGroup.create(forms = updatedForm),
        )
      }

      else -> state
    }
  }
}
