package com.dmdbrands.gurus.weight.features.manualEntry.viewmodel

import com.dmdbrands.gurus.weight.core.shared.utilities.ConversionTools
import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.common.BabyProfile
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.features.common.components.DateTimeValue
import com.dmdbrands.gurus.weight.features.common.helper.form.AppValidatorConfig
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup
import com.dmdbrands.gurus.weight.features.common.helper.form.FormValidations
import com.dmdbrands.gurus.weight.features.common.helper.form.MultiFormGroup
import com.dmdbrands.gurus.weight.features.manualEntry.strings.EntryScreenStrings
import java.util.Calendar
import androidx.compose.runtime.Stable

/**
 * Form controls for weight and date/time (always present)
 */
data class WeightDateTimeFormControls(
  val weight: FormControl<String>,
  val notes: FormControl<String>,
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
                  weightUnit == WeightUnit.LB || weightUnit == WeightUnit.LB_OZ ->
                    ConversionTools.convertStoredToKg(new.toDouble())

                  else -> new.toDouble() / 10
                }
                val storedHeight = ConversionTools.convertStoredHeightToCm(height)
                val bmi = ConversionTools.calculateBMI(weight, storedHeight.toInt())
                val bmiValue = when {
                  bmi <= 0.0 -> ""
                  bmi >= AppValidatorConfig.BMI.MAX_VALUE -> AppValidatorConfig.BMI.MAX_VALUE.toString()

                  else -> bmi.toString()
                }
                generalMetrics.bodyMassIndex.onValueChange(bmiValue)
              }
            },
          ),
          notes = FormControl.create("", emptyList()),
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
                  AppValidatorConfig.VisceralFat.MIN, AppValidatorConfig.VisceralFat.MAX, false,
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
      // Warn-but-save (Balance Health parity): block only above the hard cap,
      // show an advisory warning outside the typical range, still allow save.
      val systolic = FormControl.create(
        "",
        listOf(
          FormValidations.required(),
          FormValidations.hardMaxValidator(AppValidatorConfig.Systolic.HARD_MAX),
          FormValidations.rangeWarningValidator(
            AppValidatorConfig.Systolic.WARN_MIN, AppValidatorConfig.Systolic.WARN_MAX,
          ),
        ),
      )
      val diastolic = FormControl.create(
        "",
        listOf(
          FormValidations.required(),
          FormValidations.hardMaxValidator(AppValidatorConfig.Diastolic.HARD_MAX),
          FormValidations.rangeWarningValidator(
            AppValidatorConfig.Diastolic.WARN_MIN, AppValidatorConfig.Diastolic.WARN_MAX,
          ),
        ),
      )
      wireCrossFieldWarnings(systolic, diastolic)
      val controls = BloodPressureFormControls(
        systolic = systolic,
        diastolic = diastolic,
        pulse = FormControl.create(
          "",
          listOf(
            // Pulse is required (parity with systolic/diastolic). Without this the field could be
            // left blank → pulse defaulted to 0 → the entry failed the persist gate and was
            // silently dropped behind a fake "Entry added" toast (MOB-598).
            FormValidations.required(),
            FormValidations.hardMaxValidator(AppValidatorConfig.Pulse.HARD_MAX),
            FormValidations.rangeWarningValidator(
              AppValidatorConfig.Pulse.WARN_MIN, AppValidatorConfig.Pulse.WARN_MAX,
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

    /**
     * Wires the cross-field advisory (Balance Health / bpmMobileApp4 parity): warn — but still
     * allow save — when systolic is not higher than diastolic. Each rule fires only while its own
     * field is inside the typical range, so it never collides with the range warning. Wired after
     * construction because the two controls reference each other; the value-change listeners
     * re-validate the sibling so its advisory refreshes even when only one field is edited.
     *
     * Note: [FormControl.onValueChangeListener] holds a single listener (latest wins, by design).
     * The systolic/diastolic controls therefore reserve that slot for this cross-field wiring — do
     * not register another value-change listener on them, or the sibling advisory will stop
     * refreshing (silently, with no compile error).
     */
    private fun wireCrossFieldWarnings(
      systolic: FormControl<String>,
      diastolic: FormControl<String>,
    ) {
      systolic.addValidator(
        FormValidations.systolicCrossFieldWarning(
          diastolic,
          AppValidatorConfig.Systolic.WARN_MIN,
          AppValidatorConfig.Systolic.WARN_MAX,
          EntryScreenStrings.SYSTOLIC_CROSS_WARNING,
        ),
      )
      diastolic.addValidator(
        FormValidations.diastolicCrossFieldWarning(
          systolic,
          AppValidatorConfig.Diastolic.WARN_MIN,
          AppValidatorConfig.Diastolic.WARN_MAX,
          EntryScreenStrings.DIASTOLIC_CROSS_WARNING,
        ),
      )
      systolic.onValueChangeListener { _, _ -> diastolic.validate() }
      diastolic.onValueChangeListener { _, _ -> systolic.validate() }
    }
  }
}

/**
 * Form controls for baby entry.
 */
data class BabyEntryFormControls(
  // Unit-neutral names (MOB-1223): the layout/unit follows the account's Unit Type.
  //   lb/oz → [weight]=lb + [weightOz]=oz; lb → [weight]=lb decimal; kg → [weight]=kg.
  //   [weightOz] is only surfaced in the lb/oz layout. [length] is in / cm per unit.
  val weight: FormControl<String>,
  val weightOz: FormControl<String>,
  val length: FormControl<String>,
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
    /**
     * Builds the baby form with validators matching the account's [weightUnit] (MOB-1223):
     * lb/oz keeps the whole-lb + oz bounds; lb/kg use a single decimal weight field; length is
     * validated in inches (imperial) or cm (metric). Bounds stay exclusive (Smart Baby parity).
     */
    fun create(weightUnit: WeightUnit): BabyEntryForm {
      val isLbOz = weightUnit == WeightUnit.LB_OZ
      val isMetric = weightUnit == WeightUnit.KG
      val calendar = Calendar.getInstance()
      val controls = BabyEntryFormControls(
        weight = FormControl.create(
          "",
          listOf(
            when {
              // Whole pounds (no decimal); the oz field carries the fractional part.
              isLbOz -> FormValidations.bodyCompValidator(
                AppValidatorConfig.BabyWeightLb.MIN, AppValidatorConfig.BabyWeightLb.MAX, false,
              )
              // Single decimal weight field: kg (metric) or lb (imperial decimal).
              isMetric -> FormValidations.decimalRangeValidator(
                AppValidatorConfig.BabyWeightKg.MIN, AppValidatorConfig.BabyWeightKg.MAX,
              )
              else -> FormValidations.decimalRangeValidator(
                AppValidatorConfig.BabyWeightLb.MIN, AppValidatorConfig.BabyWeightLb.MAX,
              )
            },
            FormValidations.required(),
          ),
        ),
        // oz uses the adult weight-field input (BODY_COMP implicit 1-decimal), so it validates
        // with bodyCompValidator on the raw digit string (e.g. "159" → 15.9). Only shown in lb/oz.
        weightOz = FormControl.create(
          "",
          listOf(
            FormValidations.bodyCompValidator(
              AppValidatorConfig.BabyWeightOz.MIN, AppValidatorConfig.BabyWeightOz.MAX,
            ),
          ),
        ),
        // Length uses the ounces-style BODY_COMP implicit 1-decimal input, so it validates with
        // bodyCompValidator on the raw digit string (e.g. "205" → 20.5 in / cm). (MOB-1223)
        length = FormControl.create(
          "",
          listOf(
            if (isMetric) {
              FormValidations.bodyCompValidator(
                AppValidatorConfig.BabyLengthCm.MIN, AppValidatorConfig.BabyLengthCm.MAX,
              )
            } else {
              FormValidations.bodyCompValidator(
                AppValidatorConfig.BabyHeight.MIN, AppValidatorConfig.BabyHeight.MAX,
              )
            },
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

  // Captures the baby the form was built for so a save lands on THAT baby, not whatever the
  // global productSelectionManager.selectedProduct happens to be at save time (MOB-1449).
  data class Baby(
    val form: MultiFormGroup<BabyEntryForm>,
    val profile: BabyProfile,
  ) : ActiveEntryForm() {
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
  // Baby manual entry follows the account's MEASUREMENT unit (My Kids), which is distinct from
  // [weightMode] (the adult My Weight unit) and can differ from it (MOB-1223). Derived from
  // account.measurementUnits via MeasurementUnits.toWeightUnit().
  val babyWeightMode: WeightUnit = WeightUnit.LB_OZ,
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

  /** Updates the baby measurement unit (My Kids), derived from account.measurementUnits. */
  data class UpdateBabyUnit(val weightUnit: WeightUnit) : EntryIntent
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

      is EntryIntent.UpdateBabyUnit -> {
        state.copy(babyWeightMode = intent.weightUnit)
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
