package com.dmdbrands.gurus.weight.features.manualEntry.strings

/**
 * Strings used in the EntryScreen UI.
 */
object EntryScreenStrings {
    /** Title for the manual entry screen. */
    const val Title = "Manual Entry"

    /** Label for the value input field. */
    const val WEIGHT_LABEL = "weight"

    /** Label for the height input field. */
    const val HeightLabel = "Height"

    /** Label for the date & time input field. */
    const val DATE_LABEL = "Date"

    /** Label for the note input field. */
    const val NoteLabel = "Note"

    /** Text for the save button. */
    const val SaveButton = "Save"

    /** Label for the body mass index (BMI) input field. */
    const val BODY_MASS_INDEX_LABEL = "bmi"

    // MOB-1171: body-metric fields carry their unit as a right-edge suffix (AppInput's
    // trailingText slot) rather than baked into the label — standardised with the weight
    // field. Labels below are the base text; the matching *_UNIT is rendered as "(unit)"
    // pinned to the field's right edge.

    /** Label for the body fat input field. */
    const val BODY_FAT_LABEL = "body fat"
    const val BODY_FAT_UNIT = "%"

    /** Label for the muscle mass input field. */
    const val MUSCLE_MASS_LABEL = "muscle mass"
    const val MUSCLE_MASS_UNIT = "%"

    /** Label for the body water input field. */
    const val BODY_WATER_LABEL = "body water"
    const val BODY_WATER_UNIT = "%"

    /** Label for the heart rate input field. */
    const val HEART_RATE_LABEL = "heart rate"
    const val HEART_RATE_UNIT = "bpm"

    /** Label for the bone mass input field. */
    const val BONE_MASS_LABEL = "bone mass"
    const val BONE_MASS_UNIT = "%"

    /** Label for the visceral fat input field. */
    const val VISCERAL_FAT_LABEL = "visceral fat"
    const val VISCERAL_FAT_UNIT = "Lv."

    /** Label for the subcutaneous fat input field. */
    const val SUBCUTANEOUS_FAT_LABEL = "subcutaneous fat"
    const val SUBCUTANEOUS_FAT_UNIT = "%"

    /** Label for the protein input field. */
    const val PROTEIN_LABEL = "protein"
    const val PROTEIN_UNIT = "%"

    /** Label for the skeletal muscles input field. */
    const val SKELETAL_MUSCLES_LABEL = "skeletal muscles"
    const val SKELETAL_MUSCLES_UNIT = "%"

    /** Label for the BMR input field. */
    const val BMR_LABEL = "basal metabolic rate"
    const val BMR_UNIT = "kcal"

    /** Label for the metabolic age input field. */
    const val METABOLIC_AGE_LABEL = "metabolic age"
    const val METABOLIC_AGE_UNIT = "yrs"

    /** Title for the metrics section card. */
    const val METRICS_SECTION_TITLE = "Body Metrics"

    /** Optional subheading for the metrics section card. */
    const val METRICS_SECTION_SUBHEADING = "(optional)"

  const val EntryAdded = "Entry added."
  const val EntryAddedTitle = "Success!"

  const val EntryErrorTitle = "Error saving new entry!"
  const val EntryErrorMessage = "Please try again."

  // Baby: reject entries dated before the baby's birthdate (matches Smart Baby / babyApp).
  const val EntryBeforeBirthdate = "Unable to add entries before a babys first birth date"

  // Blood pressure
  const val SYSTOLIC_LABEL = "systolic"
  const val DIASTOLIC_LABEL = "diastolic"
  const val PULSE_LABEL = "pulse"
  const val NOTES_LABEL = "notes"

  // Cross-field advisory warnings when systolic is not higher than diastolic
  // (Balance Health / bpmMobileApp4 parity). Non-blocking — the reading still saves.
  const val SYSTOLIC_CROSS_WARNING = "Systolic should be higher than diastolic"
  const val DIASTOLIC_CROSS_WARNING = "Diastolic should be lower than systolic"

  /** Maximum character limit enforced on measurement note text (MOB-438). */
  const val NOTES_MAX_LENGTH = 280

  // Baby (MOB-1223): field labels name the metric ("weight" / "length"); the unit is rendered
  // as a right-edge "(unit)" suffix via AppInput.trailingText, matching the weight/BPM pattern.
  // The layout + unit follow the account's Unit Type (no on-screen toggle):
  //   lb/oz → two weight fields (lb + oz) + length in; lb → one weight field (lb) + length in;
  //   kg   → one weight field (kg) + length cm.
  const val LENGTH_LABEL = "length"
  // The lb/oz layout labels the pounds field "weight (lb)" and the ounces field "ounces (oz)".
  const val OUNCES_LABEL = "ounces"
  const val BABY_WEIGHT_LB_UNIT = "lb"
  const val BABY_WEIGHT_OZ_UNIT = "oz"
  const val BABY_WEIGHT_KG_UNIT = "kg"
  const val BABY_LENGTH_IN_UNIT = "in"
  const val BABY_LENGTH_CM_UNIT = "cm"

  // region Accessibility (TalkBack)
  /** Chevron-icon label when the metrics card is expanded (tapping collapses it). */
  const val accMetricsCollapseLabel = "Collapse"

  /** Chevron-icon label when the metrics card is collapsed (tapping expands it). */
  const val accMetricsExpandLabel = "Expand"
  // endregion
}
