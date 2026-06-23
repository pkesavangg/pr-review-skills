package com.dmdbrands.gurus.weight.features.common.helper.form

import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.features.common.helper.ScaleDataHelper
import com.dmdbrands.gurus.weight.features.signup.model.SignupFormControls
import java.util.Calendar

object ValidationType {
  const val MATCH_PASSWORD = "matchPassword"
  const val NOT_SAME = "notSame"
  const val REQUIRED = "required"
  const val EMAIL = "Invalid email"
  const val MIN_LENGTH = "Invalid"
  const val MAX_LENGTH = "max_length"
  const val PATTERN = "pattern"
  const val NOT_IN_RANGE = "not_in_range"
  const val GREATER = "greater"
  const val LESSER = "lesser"
  const val FUTURE_TIME = "future_time"
  const val WEIGHT_MATCH = "weightMatch"
  const val BLANK = "blank"
  const val INVALID_SCALE_DISPLAY_NAME = "invalidScaleDisplayName"
  const val DUPLICATE = "duplicate"
  const val WARNING = "warning"
  const val MAX_LIMIT = "max_limit"
}

object ValidationMessages {
  const val RANGE = "value must be between %d and %d"
  const val BETWEEN_WARNING = "this number should be between %d-%d"
  const val INVALID_NUMBER = "invalid number"
  const val INVALID_EMAIL = "must use a valid email"
  const val EMAIL_INVALID_FORMAT = "must use a valid email"
  const val PATTERN = "invalid"
  const val NOT_SAME = "value should not be same as other field"
  const val GREATER_THAN = "value should be greater than %s"
  const val LESS_THAN = "value should be less than %s"
  const val FUTURE_TIME = "date must not be in the future"
  const val SKU = "model number invalid"
  const val REQUIRED = "this field is required"
  const val PASSWORD_MISMATCH = "both passwords must match"
  const val NO_WHITESPACE = "this field is required"
  const val BLANK = "this field is required"
  const val INVALID_WEIGHT = "invalid weight"
  const val KG_RANGE = "Value should be between 0 kg and 450 kg"
  const val LB_RANGE = "Value should be between 0 lbs and 999 lbs"
  const val WEIGHT_MATCH = "value should not be equal to starting weight"
  const val DUPLICATE = "value already exists"
}

object FormValidations {
  fun required(customMessage: String? = null): Validator<Any> =
    { value ->
      if (value.toString().isEmpty()) {
        ValidationError(ValidationType.REQUIRED, customMessage ?: ValidationMessages.REQUIRED)
      } else {
        null
      }
    }

  fun minLength(
    length: Int,
    fieldName: String = "Field",
    customMessage: String? = null,
    allowSpaces: Boolean = false,
  ): Validator<String> =
    { value ->
      val valueToCheck = if (allowSpaces) value else value.trim()
      if (valueToCheck.length < length) {
        ValidationError(ValidationType.MIN_LENGTH, customMessage ?: "Minimum of $length characters needed")
      } else {
        null
      }
    }

  fun maxLength(
    length: Int,
    fieldName: String? = null,
    customMessage: String? = null,
    allowSpaces: Boolean = false,
  ): Validator<String> =
    { value ->
      val valueToCheck = if (allowSpaces) value else value.trim()
      if (valueToCheck.length > length) {
        ValidationError(
          ValidationType.MAX_LENGTH,
          customMessage ?: if(fieldName.isNullOrEmpty()) "maximum value should be $length" else "$fieldName should not exceed $length characters"
        )
      } else {
        null
      }
    }

  fun email(customMessage: String? = null): Validator<String> =
    { value ->
      val trimmedValue = value.trim()
      if (!AppValidatorConfig.Email.PATTERN.matches(trimmedValue)) {
        ValidationError(ValidationType.EMAIL, customMessage ?: ValidationMessages.EMAIL_INVALID_FORMAT)
      } else {
        null
      }
    }

  fun pattern(pattern: String): Validator<String> =
    { value ->
      if (!pattern.toRegex().matches(value)) {
        ValidationError(ValidationType.PATTERN, ValidationMessages.PATTERN)
      } else {
        null
      }
    }

  /**
   * Fails when the trimmed, case-insensitive value matches any entry in [existingValues].
   * Used to enforce uniqueness against a snapshot of sibling values (e.g. baby names already
   * added during signup). Exclude the value being edited from [existingValues] so re-saving an
   * unchanged value does not flag itself.
   */
  fun uniqueValue(
    existingValues: List<String>,
    customMessage: String? = null,
  ): Validator<String> {
    val normalized = existingValues.map { it.trim().lowercase() }
    return { value ->
      if (value.trim().lowercase() in normalized) {
        ValidationError(ValidationType.DUPLICATE, customMessage ?: ValidationMessages.DUPLICATE)
      } else {
        null
      }
    }
  }

  fun notSame(other: FormControl<String>): Validator<String> =
    { value ->
      if (value.isNotEmpty() && value == other.value) {
        ValidationError(ValidationType.NOT_SAME, ValidationMessages.NOT_SAME)
      } else {
        null
      }
    }

  fun range(range: IntRange): Validator<String> =
    { value ->
      try {
        val intValue = value.toInt()
        if (intValue !in range) {
          ValidationError(
            ValidationType.NOT_IN_RANGE,
            String.format(ValidationMessages.RANGE, range.first, range.last),
          )
        } else {
          null
        }
      } catch (e: NumberFormatException) {
        ValidationError(ValidationType.NOT_IN_RANGE, ValidationMessages.INVALID_NUMBER)
      }
    }

  fun greaterThan(other: FormControl<String>): Validator<String> =
    { value ->
      val intValue = value.toIntOrNull()
      val otherValue = other.value.toIntOrNull()
      if (value.isNotEmpty() && intValue != null && otherValue != null && intValue < otherValue) {
        ValidationError(ValidationType.LESSER, String.format(ValidationMessages.GREATER_THAN, other.value))
      } else {
        null
      }
    }

  fun lesserThan(other: FormControl<String>): Validator<String> =
    { value ->
      val intValue = value.toIntOrNull()
      val otherValue = other.value.toIntOrNull()
      if (value.isNotEmpty() && intValue != null && otherValue != null && intValue > otherValue) {
        ValidationError(ValidationType.GREATER, String.format(ValidationMessages.LESS_THAN, other.value))
      } else {
        null
      }
    }

  fun futureTime(): Validator<Calendar> =
    { value ->
      val currTime = Calendar.getInstance()
      if (value.timeInMillis > currTime.timeInMillis) {
        ValidationError(ValidationType.FUTURE_TIME, ValidationMessages.FUTURE_TIME)
      } else {
        null
      }
    }

  fun skuValidator(): Validator<String> = { value ->
    val sku = value.trim()
    // Use helper to check if SKU exists (handles variant SKU mapping e.g., 0022 -> 0383)
    val scaleInfo = ScaleDataHelper.findScaleInfoBySku(sku)
    when {
      sku.isBlank() -> null // Don't show error for empty field, button will be disabled instead
      scaleInfo != null -> null
      else -> ValidationError(ValidationType.PATTERN, ValidationMessages.SKU)
    }
  }

  fun weightValidator(unitType: WeightUnit? = WeightUnit.LB): Validator<String> =
    { value ->
      if (value.isNotBlank()) {
        val decimalValue =
          if (value.length > 1) {
            value.dropLast(1) + "." + value.takeLast(1)
          } else {
            "0.$value"
          }
        val v = decimalValue.toFloatOrNull()
        if (v == null) {
          ValidationError(ValidationType.NOT_IN_RANGE, ValidationMessages.INVALID_WEIGHT)
        } else {
          if (unitType == WeightUnit.KG) {
            val unit = "kg"
            when {
              v <= AppValidatorConfig.WeightKg.MIN  ->
                ValidationError(
                  ValidationType.GREATER,
                  String.format(ValidationMessages.GREATER_THAN, "${AppValidatorConfig.WeightKg.MIN} $unit")
                )
              v >= AppValidatorConfig.WeightKg.MAX ->
                ValidationError(
                  ValidationType.LESSER,
                  String.format(ValidationMessages.LESS_THAN, "${AppValidatorConfig.WeightKg.MAX} $unit")
                )

              else -> null
            }
          } else {
            val unit = "lbs"
            when {
              v <= AppValidatorConfig.WeightLb.MIN  ->
                ValidationError(
                  ValidationType.GREATER,
                  String.format(ValidationMessages.GREATER_THAN, "${AppValidatorConfig.WeightLb.MIN} $unit")
                )
              v >= AppValidatorConfig.WeightLb.MAX ->
                ValidationError(
                  ValidationType.LESSER,
                  String.format(ValidationMessages.LESS_THAN, "${AppValidatorConfig.WeightLb.MAX} $unit")
                )

              else -> null
            }
          }
        }
      } else {
        null
      }
    }

  fun bodyCompValidator(
    min: Int = AppValidatorConfig.BodyComp.MIN,
    max: Int = AppValidatorConfig.BodyComp.MAX,
    allowDecimal: Boolean = true,
  ): Validator<String> =
    { value ->
      if (value.isNotBlank()) {
        val decimalValue =
          if (allowDecimal) {
            if (value.length > 1) value.dropLast(1) + "." + value.takeLast(1) else "0." + value
          } else {
            value
          }

        val v =
          if (allowDecimal) {
            decimalValue.toFloatOrNull()
          } else {
            value.toIntOrNull()?.toFloat()
          }

        if (v == null) {
          ValidationError(ValidationType.NOT_IN_RANGE, ValidationMessages.INVALID_NUMBER)
        } else {
          when {
            v <= min  ->
              ValidationError(
                ValidationType.GREATER,
                String.format(ValidationMessages.GREATER_THAN, min)
              )
            v >= max ->
              ValidationError(
                ValidationType.LESSER,
                String.format(ValidationMessages.LESS_THAN, max)
              )

            else -> null
          }
        }
      } else {
        null
      }
    }

  /**
   * This validator is specifically for confirm password field to check against password field
   */
  fun confirmPasswordMatch(formGroup: () -> FormGroup<SignupFormControls>): Validator<String> =
    { confirmPasswordValue ->
      val form = formGroup()
      val passwordValue = form.controls.password.value

      // Only show mismatch error if both fields have values and they don't match
      if (confirmPasswordValue.isNotEmpty() &&
        passwordValue.isNotEmpty() &&
        confirmPasswordValue != passwordValue
      ) {
        ValidationError(ValidationType.MATCH_PASSWORD, ValidationMessages.PASSWORD_MISMATCH)
      } else {
        null
      }
    }

  /**
   * Advisory (non-blocking) range check for manual entry. Returns a WARNING-severity
   * result when an integer value falls outside [min]..[max]; the value still saves.
   * Mirrors Balance Health's `setWarningMessages` for systolic/diastolic/pulse.
   * Blank values are left to [required]; non-numeric/out-of-hard-cap are left to
   * [hardMaxValidator], so this only flags an in-cap-but-atypical number.
   */
  fun rangeWarningValidator(min: Int, max: Int): Validator<String> =
    { value ->
      val v = value.toIntOrNull()
      if (v != null && (v < min || v > max)) {
        ValidationError(
          ValidationType.WARNING,
          String.format(ValidationMessages.BETWEEN_WARNING, min, max),
          ValidationSeverity.WARNING,
        )
      } else {
        null
      }
    }

  /**
   * Blocking upper-bound check for manual entry. Returns an ERROR when an integer
   * value exceeds [max] (or isn't a valid whole number). Mirrors Balance Health's
   * hard 500 cap on systolic/diastolic/pulse. Blank is left to [required].
   */
  fun hardMaxValidator(max: Int): Validator<String> =
    { value ->
      if (value.isBlank()) {
        null
      } else {
        val v = value.toIntOrNull()
        when {
          v == null -> ValidationError(ValidationType.NOT_IN_RANGE, ValidationMessages.INVALID_NUMBER)
          v > max -> ValidationError(
            ValidationType.MAX_LIMIT,
            String.format(ValidationMessages.LESS_THAN, max),
          )
          else -> null
        }
      }
    }

  /**
   * Exclusive decimal range check for free decimal entry (e.g. baby oz/length).
   * Parses the literal decimal string (not the implicit-decimal [bodyCompValidator]
   * transform) so it pairs with [AppInputType.DECIMAL_STRING]. Value must be
   * strictly inside ([min], [max]). Blank is left to [required] (optional fields
   * simply pass when empty).
   */
  fun decimalRangeValidator(min: Int, max: Int): Validator<String> =
    { value ->
      if (value.isBlank()) {
        null
      } else {
        val v = value.toDoubleOrNull()
        when {
          v == null -> ValidationError(ValidationType.NOT_IN_RANGE, ValidationMessages.INVALID_NUMBER)
          v <= min -> ValidationError(
            ValidationType.GREATER,
            String.format(ValidationMessages.GREATER_THAN, min),
          )
          v >= max -> ValidationError(
            ValidationType.LESSER,
            String.format(ValidationMessages.LESS_THAN, max),
          )
          else -> null
        }
      }
    }

  /**
   * Validator that checks if the value contains only whitespace characters.
   * This matches the TypeScript noWhiteSpace validator behavior.
   * Returns error if value has length > 0 but trim().length == 0
   */
  fun noWhiteSpace(): Validator<String> =
    { value ->
      if (value.isNotEmpty() && value.trim().isEmpty()) {
        ValidationError(ValidationType.BLANK, ValidationMessages.BLANK)
      } else {
        null
      }
    }

  /**
   * Validator for goal weight to ensure it's different from current weight when goal type is losegain.
   * This validator checks if the goal weight matches the current weight and shows an error if they're the same.
   *
   * @param currentWeightControl The current weight form control to compare against
   * @param goalTypeControl The goal type form control to check if it's losegain
   * @return Validator that returns error if weights match for losegain goal type
   */
  fun weightMatchValidator(
    currentWeightControl: FormControl<String>,
    goalTypeControl: FormControl<String>
  ): Validator<String> =
    { goalWeightValue ->
      val currentWeightValue = currentWeightControl.value
      val goalTypeValue = goalTypeControl.value

      // Only validate if goal type is losegain and both weights have values
      if ((goalTypeValue == "losegain" || goalTypeValue == "gain" || goalTypeValue == "lose") &&
          goalWeightValue.isNotEmpty() &&
          currentWeightValue.isNotEmpty() &&
          goalWeightValue == currentWeightValue) {
        ValidationError(ValidationType.WEIGHT_MATCH, ValidationMessages.WEIGHT_MATCH)
      } else {
        null
      }
    }

  /**
   * Validator that checks if the scale display name is "guest" (case-insensitive).
   * This matches the Angular scaleDisplaynameValidator behavior.
   * Returns error if the trimmed value (case-insensitive) equals "guest".
   *
   * @param customMessage Optional custom error message. If not provided, uses default message.
   * @return Validator that returns error if value is "guest" (case-insensitive)
   */
  fun scaleDisplayNameValidator(customMessage: String? = null): Validator<String> =
    { value ->
      val trimmedValue = value.trim()
      if (trimmedValue.isNotEmpty() && trimmedValue.equals("guest", ignoreCase = true)) {
        ValidationError(
          ValidationType.INVALID_SCALE_DISPLAY_NAME,
          customMessage ?: "user name unavailable"
        )
      } else {
        null
      }
    }
}
