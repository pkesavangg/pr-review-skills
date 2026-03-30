package com.dmdbrands.gurus.weight.features.manualEntry.viewmodel

import com.dmdbrands.gurus.weight.core.shared.utilities.ConversionTools
import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.features.common.components.DateTimeValue
import com.dmdbrands.gurus.weight.features.common.helper.form.AppValidatorConfig
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup
import com.dmdbrands.gurus.weight.features.common.helper.form.FormValidations
import com.dmdbrands.gurus.weight.features.common.helper.form.MultiFormGroup
import java.util.Calendar
import androidx.compose.runtime.Stable

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
    /**
     * Formats a Double value to a String representation for form controls.
     * Formats to 1 decimal place, then multiplies by 10 and converts to int.
     * Example: 35.5 -> "355", 28.2 -> "282"
     *
     * @param value The Double value to format, can be null
     * @return Formatted string representation, or empty string if value is null
     */
    private fun formatScaleEntryValue(value: Double?): String {
      return value?.let {
        String.format("%.1f", it).toFloat().times(10).toInt().toString()
      } ?: ""
    }

    fun create(
      includeR4ScaleMetrics: Boolean = false,
      weightUnit: WeightUnit? = null,
      height: Int? = 0,
      scaleEntry: com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry? = null,
      isValueChangeAllowed: (String, String) -> Boolean = { _, _ -> true }
    ): EntryForm {
      val calendar = Calendar.getInstance()
      val currentTimeMillis = calendar.timeInMillis
      val hour = calendar.get(Calendar.HOUR_OF_DAY)
      val minute = calendar.get(Calendar.MINUTE)
      val generalMetrics =
        GeneralMetricsFormControls(
          bodyMassIndex = FormControl.create(
            formatScaleEntryValue(scaleEntry?.scale?.scaleEntry?.bmi),
            listOf(FormValidations.bodyCompValidator()),
          ),
          bodyFat = FormControl.create(
            formatScaleEntryValue(scaleEntry?.scale?.scaleEntry?.bodyFat),
            listOf(FormValidations.bodyCompValidator()),
          ),
          muscleMass = FormControl.create(
            formatScaleEntryValue(scaleEntry?.scale?.scaleEntry?.muscleMass),
            listOf(FormValidations.bodyCompValidator()),
          ),
          bodyWater = FormControl.create(
            formatScaleEntryValue(scaleEntry?.scale?.scaleEntry?.water),
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
              formatScaleEntryValue(displayWeight)
            } else "",
            listOf(
              FormValidations.weightValidator(weightUnit),
              FormValidations.required(),
            ),
            onValueChangeCallback = { old, new ->
              if (height != null && isValueChangeAllowed(old, new)) {
                val weight = when {
                  new.isBlank() -> 0.0
                  weightUnit == WeightUnit.LB -> ConversionTools.convertStoredToKg(
                    new.toDouble(),
                  )

                  else -> new.toDouble() / 10
                }
                val storedHeight = ConversionTools.convertStoredHeightToCm(height)
                val bmi = ConversionTools.calculateBMI(weight, storedHeight)
                val bmiValue = when {
                  bmi <= 0.0 -> ""
                  bmi >= AppValidatorConfig.BMI.MAX_VALUE -> AppValidatorConfig.BMI.MAX_VALUE.toString()

                  else -> bmi.toString()
                }
                generalMetrics.bodyMassIndex.onValueChange(bmiValue)
              }
            },
          ),
          dateTime = FormControl.create(
            DateTimeValue.DateTime(millis = currentTimeMillis, hour = hour, minute = minute),
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
                  AppValidatorConfig.HeartRate.MIN, AppValidatorConfig.HeartRate.MAX, false,
                ),
              ),
            ),
            boneMass = FormControl.create("", listOf(FormValidations.bodyCompValidator())),
            visceralFat = FormControl.create(
              "",
              listOf(
                FormValidations.bodyCompValidator(
                  AppValidatorConfig.VisceralAge.MIN, AppValidatorConfig.VisceralAge.MAX, false,
                ),
              ),
            ),
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

/**
 * Form controls for blood pressure entry.
 */
data class BloodPressureFormControls(
  val systolic: FormControl<String>,
  val diastolic: FormControl<String>,
  val pulse: FormControl<String>,
  val notes: FormControl<String>,
  val dateTime: FormControl<DateTimeValue>,
)

/**
 * Blood pressure entry form.
 */
data class BloodPressureEntryForm(
  val bloodPressure: FormGroup<BloodPressureFormControls>,
) {
  companion object {
    fun create(): BloodPressureEntryForm {
      val calendar = Calendar.getInstance()
      val controls = BloodPressureFormControls(
        systolic = FormControl.create(
          "",
          listOf(
            FormValidations.bodyCompValidator(
              AppValidatorConfig.Systolic.MIN, AppValidatorConfig.Systolic.MAX, false,
            ),
            FormValidations.required(),
          ),
        ),
        diastolic = FormControl.create(
          "",
          listOf(
            FormValidations.bodyCompValidator(
              AppValidatorConfig.Diastolic.MIN, AppValidatorConfig.Diastolic.MAX, false,
            ),
            FormValidations.required(),
          ),
        ),
        pulse = FormControl.create(
          "",
          listOf(
            FormValidations.bodyCompValidator(
              AppValidatorConfig.Pulse.MIN, AppValidatorConfig.Pulse.MAX, false,
            ),
          ),
        ),
        notes = FormControl.create("", emptyList()),
        dateTime = FormControl.create(
          DateTimeValue.DateTime(
            millis = calendar.timeInMillis,
            hour = calendar.get(Calendar.HOUR_OF_DAY),
            minute = calendar.get(Calendar.MINUTE),
          ),
          emptyList(),
        ),
      )
      return BloodPressureEntryForm(bloodPressure = FormGroup(controls))
    }
  }
}

/**
 * Form controls for baby entry.
 */
data class BabyEntryFormControls(
  val pounds: FormControl<String>,
  val ounces: FormControl<String>,
  val inches: FormControl<String>,
  val notes: FormControl<String>,
  val dateTime: FormControl<DateTimeValue>,
)

/**
 * Baby entry form.
 */
data class BabyEntryForm(
  val baby: FormGroup<BabyEntryFormControls>,
) {
  companion object {
    fun create(): BabyEntryForm {
      val calendar = Calendar.getInstance()
      val controls = BabyEntryFormControls(
        pounds = FormControl.create(
          "",
          listOf(
            FormValidations.bodyCompValidator(
              AppValidatorConfig.BabyWeightLb.MIN, AppValidatorConfig.BabyWeightLb.MAX, false,
            ),
            FormValidations.required(),
          ),
        ),
        ounces = FormControl.create(
          "",
          listOf(
            FormValidations.bodyCompValidator(
              AppValidatorConfig.BabyWeightOz.MIN, AppValidatorConfig.BabyWeightOz.MAX, false,
            ),
          ),
        ),
        inches = FormControl.create(
          "",
          listOf(
            FormValidations.bodyCompValidator(
              AppValidatorConfig.BabyHeight.MIN, AppValidatorConfig.BabyHeight.MAX, false,
            ),
          ),
        ),
        notes = FormControl.create("", emptyList()),
        dateTime = FormControl.create(
          DateTimeValue.DateTime(
            millis = calendar.timeInMillis,
            hour = calendar.get(Calendar.HOUR_OF_DAY),
            minute = calendar.get(Calendar.MINUTE),
          ),
          emptyList(),
        ),
      )
      return BabyEntryForm(baby = FormGroup(controls))
    }
  }
}

/**
 * Sealed class representing the active entry form for each product type.
 * Only one form is active at a time.
 */
sealed class ActiveEntryForm {
  abstract val isValid: Boolean
  abstract val isDirty: Boolean

  data class Weight(val form: MultiFormGroup<EntryForm>) : ActiveEntryForm() {
    override val isValid get() = form.isValid
    override val isDirty get() = form.isDirty || form.isTouched
  }

  data class BloodPressure(val form: MultiFormGroup<BloodPressureEntryForm>) : ActiveEntryForm() {
    override val isValid get() = form.isValid
    override val isDirty get() = form.isDirty || form.isTouched
  }

  data class Baby(val form: MultiFormGroup<BabyEntryForm>) : ActiveEntryForm() {
    override val isValid get() = form.isValid
    override val isDirty get() = form.isDirty || form.isTouched
  }
}

@Stable
data class EntryState(
  val activeForm: ActiveEntryForm = ActiveEntryForm.Weight(
    form = MultiFormGroup.create(forms = EntryForm.create()),
  ),
  val weightMode: WeightUnit = WeightUnit.LB,
  val isLoading: Boolean = false,
  val isMetricFieldsExpandedInitially: Boolean = false,
  val dashboardType: DashboardType = DashboardType.DASHBOARD_4_METRICS,
) : IReducer.State {
  /** Convenience accessor for the weight form (backward compatibility). */
  val form: MultiFormGroup<EntryForm>
    get() = (activeForm as? ActiveEntryForm.Weight)?.form
      ?: MultiFormGroup.create(forms = EntryForm.create())
}

/**
 * Intent for entry actions, such as loading, selecting, adding, and deleting entries.
 */
sealed interface EntryIntent : IReducer.Intent {
  data object UpdateOnRelaunch : EntryIntent
  data object Save : EntryIntent
  data object EarlyExit : EntryIntent

  /** Updates the weight form (wraps into ActiveEntryForm.Weight). */
  data class UpdateForm(val form: MultiFormGroup<EntryForm>) : EntryIntent

  /** Switches the active form to a different product type. */
  data class UpdateActiveForm(val activeForm: ActiveEntryForm) : EntryIntent

  data class UpdateWeightUnit(val weightUnit: WeightUnit) : EntryIntent
  data class UpdateDashboardType(val dashboardType: DashboardType) : EntryIntent
  data class UpdateMetricFieldsExpandedStatus(val isExpanded: Boolean) : EntryIntent
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
        state.copy(activeForm = ActiveEntryForm.Weight(form = intent.form))
      }

      is EntryIntent.UpdateActiveForm -> {
        state.copy(activeForm = intent.activeForm)
      }

      is EntryIntent.UpdateWeightUnit -> {
        state.copy(weightMode = intent.weightUnit)
      }

      is EntryIntent.UpdateDashboardType -> {
        state.copy(dashboardType = intent.dashboardType)
      }

      is EntryIntent.UpdateMetricFieldsExpandedStatus -> {
        state.copy(isMetricFieldsExpandedInitially = intent.isExpanded)
      }

      is EntryIntent.LoadAppSyncData -> {
        val scaleEntry = intent.scaleEntry
        val currentForm = state.form.forms
        val updatedForm = EntryForm.create(
          includeR4ScaleMetrics = currentForm.r4ScaleMetrics != null,
          weightUnit = state.weightMode,
          height = intent.height,
          scaleEntry = scaleEntry,
          isValueChangeAllowed = { _, _ ->
            !state.form.forms.generalMetrics.controls.bodyMassIndex.touched
          },
        )
        state.copy(
          activeForm = ActiveEntryForm.Weight(
            form = MultiFormGroup.create(forms = updatedForm),
          ),
        )
      }

      else -> state
    }
  }
}
