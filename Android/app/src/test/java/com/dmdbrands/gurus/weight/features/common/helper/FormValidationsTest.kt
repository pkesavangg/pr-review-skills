package com.dmdbrands.gurus.weight.features.common.helper

import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup
import com.dmdbrands.gurus.weight.features.common.helper.form.FormValidations
import com.dmdbrands.gurus.weight.features.common.helper.form.ValidationError
import com.dmdbrands.gurus.weight.features.common.helper.form.ValidationMessages
import com.dmdbrands.gurus.weight.features.common.helper.form.ValidationSeverity
import com.dmdbrands.gurus.weight.features.common.helper.form.ValidationType
import com.dmdbrands.gurus.weight.features.signup.model.SignupFormControls
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.util.Calendar

class FormValidationsTest {

    // -------------------------------------------------------------------------
    // required
    // -------------------------------------------------------------------------

    @Test
    fun `required returns null for non-empty string`() {
        val validator = FormValidations.required()

        val result = validator("hello")

        assertThat(result).isNull()
    }

    @Test
    fun `required returns error for empty string`() {
        val validator = FormValidations.required()

        val result = validator("")

        assertThat(result).isNotNull()
        assertThat(result?.type).isEqualTo(ValidationType.REQUIRED)
    }

    @Test
    fun `required uses custom message when provided`() {
        val validator = FormValidations.required("Email is required")

        val result = validator("")

        assertThat(result?.message).isEqualTo("Email is required")
    }

    // -------------------------------------------------------------------------
    // email
    // -------------------------------------------------------------------------

    @Test
    fun `email returns null for valid email`() {
        val validator = FormValidations.email()

        val result = validator("user@example.com")

        assertThat(result).isNull()
    }

    @Test
    fun `email returns null for email with plus tag`() {
        val validator = FormValidations.email()

        val result = validator("user+tag@example.com")

        assertThat(result).isNull()
    }

    @Test
    fun `email returns null for email with subdomain`() {
        val validator = FormValidations.email()

        val result = validator("user@mail.example.com")

        assertThat(result).isNull()
    }

    @Test
    fun `email returns error for empty string`() {
        val validator = FormValidations.email()

        val result = validator("")

        assertThat(result).isNotNull()
        assertThat(result?.type).isEqualTo(ValidationType.EMAIL)
    }

    @Test
    fun `email returns error for string without at sign`() {
        val validator = FormValidations.email()

        val result = validator("userexample.com")

        assertThat(result).isNotNull()
    }

    @Test
    fun `email returns error for string without domain`() {
        val validator = FormValidations.email()

        val result = validator("user@")

        assertThat(result).isNotNull()
    }

    @Test
    fun `email returns error for string with spaces`() {
        val validator = FormValidations.email()

        val result = validator("user @example.com")

        assertThat(result).isNotNull()
    }

    @Test
    fun `email trims whitespace before validating`() {
        val validator = FormValidations.email()

        val result = validator("  user@example.com  ")

        assertThat(result).isNull()
    }

    @Test
    fun `email rejects XSS script tag`() {
        val validator = FormValidations.email()

        val result = validator("<script>alert('xss')</script>")

        assertThat(result).isNotNull()
    }

    @Test
    fun `email rejects SQL injection string`() {
        val validator = FormValidations.email()

        val result = validator("'; DROP TABLE users; --")

        assertThat(result).isNotNull()
    }

    // -------------------------------------------------------------------------
    // minLength
    // -------------------------------------------------------------------------

    @Test
    fun `minLength returns null when string meets minimum`() {
        val validator = FormValidations.minLength(8)

        val result = validator("12345678")

        assertThat(result).isNull()
    }

    @Test
    fun `minLength returns error when string is too short`() {
        val validator = FormValidations.minLength(8)

        val result = validator("1234567")

        assertThat(result).isNotNull()
        assertThat(result?.type).isEqualTo(ValidationType.MIN_LENGTH)
    }

    @Test
    fun `minLength returns error for empty string`() {
        val validator = FormValidations.minLength(1)

        val result = validator("")

        assertThat(result).isNotNull()
    }

    @Test
    fun `minLength trims whitespace by default`() {
        val validator = FormValidations.minLength(5)

        val result = validator("  ab  ")

        assertThat(result).isNotNull()
    }

    @Test
    fun `minLength allows spaces when allowSpaces is true`() {
        val validator = FormValidations.minLength(5, allowSpaces = true)

        val result = validator("  ab  ")

        assertThat(result).isNull()
    }

    // -------------------------------------------------------------------------
    // maxLength
    // -------------------------------------------------------------------------

    @Test
    fun `maxLength returns null when string is within limit`() {
        val validator = FormValidations.maxLength(50)

        val result = validator("short name")

        assertThat(result).isNull()
    }

    @Test
    fun `maxLength returns error when string exceeds limit`() {
        val validator = FormValidations.maxLength(5)

        val result = validator("toolong")

        assertThat(result).isNotNull()
        assertThat(result?.type).isEqualTo(ValidationType.MAX_LENGTH)
    }

    @Test
    fun `maxLength returns null at exact boundary`() {
        val validator = FormValidations.maxLength(5)

        val result = validator("exact")

        assertThat(result).isNull()
    }

    @Test
    fun `maxLength includes fieldName in error message when provided`() {
        val validator = FormValidations.maxLength(5, fieldName = "Display name")

        val result = validator("toolong")

        assertThat(result?.message).contains("Display name")
    }

    // -------------------------------------------------------------------------
    // pattern
    // -------------------------------------------------------------------------

    @Test
    fun `pattern returns null for matching string`() {
        val validator = FormValidations.pattern("^[0-9]{4}$")

        val result = validator("1234")

        assertThat(result).isNull()
    }

    @Test
    fun `pattern returns error for non-matching string`() {
        val validator = FormValidations.pattern("^[0-9]{4}$")

        val result = validator("abc")

        assertThat(result).isNotNull()
        assertThat(result?.type).isEqualTo(ValidationType.PATTERN)
    }

    // -------------------------------------------------------------------------
    // range
    // -------------------------------------------------------------------------

    @Test
    fun `range returns null for value within range`() {
        val validator = FormValidations.range(1..100)

        val result = validator("50")

        assertThat(result).isNull()
    }

    @Test
    fun `range returns null at lower boundary`() {
        val validator = FormValidations.range(1..100)

        val result = validator("1")

        assertThat(result).isNull()
    }

    @Test
    fun `range returns null at upper boundary`() {
        val validator = FormValidations.range(1..100)

        val result = validator("100")

        assertThat(result).isNull()
    }

    @Test
    fun `range returns error below lower boundary`() {
        val validator = FormValidations.range(1..100)

        val result = validator("0")

        assertThat(result).isNotNull()
        assertThat(result?.type).isEqualTo(ValidationType.NOT_IN_RANGE)
    }

    @Test
    fun `range returns error above upper boundary`() {
        val validator = FormValidations.range(1..100)

        val result = validator("101")

        assertThat(result).isNotNull()
    }

    @Test
    fun `range returns error for non-numeric string`() {
        val validator = FormValidations.range(1..100)

        val result = validator("abc")

        assertThat(result).isNotNull()
    }

    // -------------------------------------------------------------------------
    // notSame
    // -------------------------------------------------------------------------

    @Test
    fun `notSame returns null when values differ`() {
        val other = FormControl.create("oldpassword")
        val validator = FormValidations.notSame(other)

        val result = validator("newpassword")

        assertThat(result).isNull()
    }

    @Test
    fun `notSame returns error when values match`() {
        val other = FormControl.create("samevalue")
        val validator = FormValidations.notSame(other)

        val result = validator("samevalue")

        assertThat(result).isNotNull()
        assertThat(result?.type).isEqualTo(ValidationType.NOT_SAME)
    }

    @Test
    fun `notSame returns null when value is empty`() {
        val other = FormControl.create("something")
        val validator = FormValidations.notSame(other)

        val result = validator("")

        assertThat(result).isNull()
    }

    // -------------------------------------------------------------------------
    // greaterThan
    // -------------------------------------------------------------------------

    @Test
    fun `greaterThan returns null when value is greater`() {
        val other = FormControl.create("10")
        val validator = FormValidations.greaterThan(other)

        val result = validator("20")

        assertThat(result).isNull()
    }

    @Test
    fun `greaterThan returns error when value is less`() {
        val other = FormControl.create("20")
        val validator = FormValidations.greaterThan(other)

        val result = validator("10")

        assertThat(result).isNotNull()
        assertThat(result?.type).isEqualTo(ValidationType.LESSER)
    }

    @Test
    fun `greaterThan returns null for empty value`() {
        val other = FormControl.create("10")
        val validator = FormValidations.greaterThan(other)

        val result = validator("")

        assertThat(result).isNull()
    }

    // -------------------------------------------------------------------------
    // lesserThan
    // -------------------------------------------------------------------------

    @Test
    fun `lesserThan returns null when value is less`() {
        val other = FormControl.create("20")
        val validator = FormValidations.lesserThan(other)

        val result = validator("10")

        assertThat(result).isNull()
    }

    @Test
    fun `lesserThan returns error when value is greater`() {
        val other = FormControl.create("10")
        val validator = FormValidations.lesserThan(other)

        val result = validator("20")

        assertThat(result).isNotNull()
        assertThat(result?.type).isEqualTo(ValidationType.GREATER)
    }

    // -------------------------------------------------------------------------
    // futureTime
    // -------------------------------------------------------------------------

    @Test
    fun `futureTime returns null for past date`() {
        val validator = FormValidations.futureTime()
        val pastDate = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1)
        }

        val result = validator(pastDate)

        assertThat(result).isNull()
    }

    @Test
    fun `futureTime returns error for future date`() {
        val validator = FormValidations.futureTime()
        val futureDate = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 2)
        }

        val result = validator(futureDate)

        assertThat(result).isNotNull()
        assertThat(result?.type).isEqualTo(ValidationType.FUTURE_TIME)
    }

    // -------------------------------------------------------------------------
    // weightValidator — LB
    // -------------------------------------------------------------------------

    @Test
    fun `weightValidator LB returns null for valid weight`() {
        val validator = FormValidations.weightValidator(WeightUnit.LB)

        // "1500" → decimal "150.0" which is in range
        val result = validator("1500")

        assertThat(result).isNull()
    }

    @Test
    fun `weightValidator LB returns error for zero weight`() {
        val validator = FormValidations.weightValidator(WeightUnit.LB)

        // "00" → decimal "0.0" which is <= MIN
        val result = validator("00")

        assertThat(result).isNotNull()
    }

    @Test
    fun `weightValidator LB returns error above max`() {
        val validator = FormValidations.weightValidator(WeightUnit.LB)

        // "9999" → decimal "999.9" which is >= MAX (999)
        val result = validator("9999")

        assertThat(result).isNotNull()
    }

    @Test
    fun `weightValidator LB returns null for blank value`() {
        val validator = FormValidations.weightValidator(WeightUnit.LB)

        val result = validator("")

        assertThat(result).isNull()
    }

    @Test
    fun `weightValidator LB returns error for non-numeric value`() {
        val validator = FormValidations.weightValidator(WeightUnit.LB)

        val result = validator("abc")

        assertThat(result).isNotNull()
    }

    // -------------------------------------------------------------------------
    // weightValidator — KG
    // -------------------------------------------------------------------------

    @Test
    fun `weightValidator KG returns null for valid weight`() {
        val validator = FormValidations.weightValidator(WeightUnit.KG)

        // "800" → decimal "80.0" which is in range
        val result = validator("800")

        assertThat(result).isNull()
    }

    @Test
    fun `weightValidator KG returns error above max`() {
        val validator = FormValidations.weightValidator(WeightUnit.KG)

        // "4500" → decimal "450.0" which is >= MAX (450)
        val result = validator("4500")

        assertThat(result).isNotNull()
    }

    @Test
    fun `weightValidator KG returns error for zero`() {
        val validator = FormValidations.weightValidator(WeightUnit.KG)

        val result = validator("00")

        assertThat(result).isNotNull()
    }

    // -------------------------------------------------------------------------
    // bodyCompValidator
    // -------------------------------------------------------------------------

    @Test
    fun `bodyCompValidator returns null for valid value`() {
        val validator = FormValidations.bodyCompValidator()

        // "250" → decimal "25.0" which is in range 0..99
        val result = validator("250")

        assertThat(result).isNull()
    }

    @Test
    fun `bodyCompValidator returns error above max`() {
        val validator = FormValidations.bodyCompValidator()

        // "999" → decimal "99.9" which is >= 99
        val result = validator("999")

        assertThat(result).isNotNull()
    }

    @Test
    fun `bodyCompValidator returns null for blank value`() {
        val validator = FormValidations.bodyCompValidator()

        val result = validator("")

        assertThat(result).isNull()
    }

    @Test
    fun `bodyCompValidator with custom range returns error outside range`() {
        val validator = FormValidations.bodyCompValidator(min = 60, max = 200)

        // "500" → decimal "50.0" which is < 60
        val result = validator("500")

        assertThat(result).isNotNull()
    }

    @Test
    fun `bodyCompValidator without decimal returns error for non-integer`() {
        val validator = FormValidations.bodyCompValidator(allowDecimal = false)

        val result = validator("12.5")

        assertThat(result).isNotNull()
    }

    // -------------------------------------------------------------------------
    // noWhiteSpace
    // -------------------------------------------------------------------------

    @Test
    fun `noWhiteSpace returns null for normal string`() {
        val validator = FormValidations.noWhiteSpace()

        val result = validator("hello")

        assertThat(result).isNull()
    }

    @Test
    fun `noWhiteSpace returns null for empty string`() {
        val validator = FormValidations.noWhiteSpace()

        val result = validator("")

        assertThat(result).isNull()
    }

    @Test
    fun `noWhiteSpace returns error for whitespace-only string`() {
        val validator = FormValidations.noWhiteSpace()

        val result = validator("   ")

        assertThat(result).isNotNull()
        assertThat(result?.type).isEqualTo(ValidationType.BLANK)
    }

    @Test
    fun `noWhiteSpace returns null for string with mixed content`() {
        val validator = FormValidations.noWhiteSpace()

        val result = validator("  hello  ")

        assertThat(result).isNull()
    }

    // -------------------------------------------------------------------------
    // scaleDisplayNameValidator
    // -------------------------------------------------------------------------

    @Test
    fun `scaleDisplayNameValidator returns null for normal name`() {
        val validator = FormValidations.scaleDisplayNameValidator()

        val result = validator("John")

        assertThat(result).isNull()
    }

    @Test
    fun `scaleDisplayNameValidator returns error for guest`() {
        val validator = FormValidations.scaleDisplayNameValidator()

        val result = validator("guest")

        assertThat(result).isNotNull()
        assertThat(result?.type).isEqualTo(ValidationType.INVALID_SCALE_DISPLAY_NAME)
    }

    @Test
    fun `scaleDisplayNameValidator returns error for Guest case-insensitive`() {
        val validator = FormValidations.scaleDisplayNameValidator()

        val result = validator("GUEST")

        assertThat(result).isNotNull()
    }

    @Test
    fun `scaleDisplayNameValidator returns null for empty string`() {
        val validator = FormValidations.scaleDisplayNameValidator()

        val result = validator("")

        assertThat(result).isNull()
    }

    // -------------------------------------------------------------------------
    // weightMatchValidator
    // -------------------------------------------------------------------------

    @Test
    fun `weightMatchValidator returns error when weights match for losegain`() {
        val currentWeight = FormControl.create("1500")
        val goalType = FormControl.create("losegain")
        val validator = FormValidations.weightMatchValidator(currentWeight, goalType)

        val result = validator("1500")

        assertThat(result).isNotNull()
        assertThat(result?.type).isEqualTo(ValidationType.WEIGHT_MATCH)
    }

    @Test
    fun `weightMatchValidator returns null when weights differ for losegain`() {
        val currentWeight = FormControl.create("1500")
        val goalType = FormControl.create("losegain")
        val validator = FormValidations.weightMatchValidator(currentWeight, goalType)

        val result = validator("1400")

        assertThat(result).isNull()
    }

    @Test
    fun `weightMatchValidator returns error when weights match for lose`() {
        val currentWeight = FormControl.create("1500")
        val goalType = FormControl.create("lose")
        val validator = FormValidations.weightMatchValidator(currentWeight, goalType)

        val result = validator("1500")

        assertThat(result).isNotNull()
    }

    @Test
    fun `weightMatchValidator returns error when weights match for gain`() {
        val currentWeight = FormControl.create("1500")
        val goalType = FormControl.create("gain")
        val validator = FormValidations.weightMatchValidator(currentWeight, goalType)

        val result = validator("1500")

        assertThat(result).isNotNull()
    }

    @Test
    fun `weightMatchValidator returns null for maintain goal type even if weights match`() {
        val currentWeight = FormControl.create("1500")
        val goalType = FormControl.create("maintain")
        val validator = FormValidations.weightMatchValidator(currentWeight, goalType)

        val result = validator("1500")

        assertThat(result).isNull()
    }

    // -------------------------------------------------------------------------
    // XSS and injection inputs
    // -------------------------------------------------------------------------

    @Test
    fun `required passes for XSS string since it is non-empty`() {
        val validator = FormValidations.required()

        val result = validator("<script>alert('xss')</script>")

        assertThat(result).isNull()
    }

    @Test
    fun `email rejects HTML injection`() {
        val validator = FormValidations.email()

        val result = validator("<img src=x onerror=alert(1)>")

        assertThat(result).isNotNull()
    }

    @Test
    fun `email rejects JavaScript protocol`() {
        val validator = FormValidations.email()

        val result = validator("javascript:alert(1)")

        assertThat(result).isNotNull()
    }

    @Test
    fun `pattern rejects SQL injection in numeric field`() {
        val validator = FormValidations.pattern("^[0-9]+$")

        val result = validator("1; DROP TABLE users")

        assertThat(result).isNotNull()
    }

    @Test
    fun `minLength passes null bytes since trim does not strip them`() {
        val validator = FormValidations.minLength(8)

        val result = validator("\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000")

        // Kotlin trim() does not strip null bytes — 8 null bytes still have length 8
        assertThat(result).isNull()
    }

    // -------------------------------------------------------------------------
    // uniqueValue
    // -------------------------------------------------------------------------

    @Test
    fun `uniqueValue returns null when value not in list`() {
        val validator = FormValidations.uniqueValue(listOf("Sally", "Tammy"))

        val result = validator("Katey")

        assertThat(result).isNull()
    }

    @Test
    fun `uniqueValue returns DUPLICATE error for exact match`() {
        val validator = FormValidations.uniqueValue(listOf("Sally", "Tammy"))

        val result = validator("Sally")

        assertThat(result).isNotNull()
        assertThat(result?.type).isEqualTo(ValidationType.DUPLICATE)
    }

    @Test
    fun `uniqueValue matches case-insensitively`() {
        val validator = FormValidations.uniqueValue(listOf("Sally"))

        val result = validator("sally")

        assertThat(result).isNotNull()
        assertThat(result?.type).isEqualTo(ValidationType.DUPLICATE)
    }

    @Test
    fun `uniqueValue trims before comparing`() {
        val validator = FormValidations.uniqueValue(listOf("Sally"))

        val result = validator("  Sally  ")

        assertThat(result).isNotNull()
    }

    @Test
    fun `uniqueValue uses custom message when provided`() {
        val validator = FormValidations.uniqueValue(listOf("Sally"), "Baby name already exists")

        val result = validator("Sally")

        assertThat(result?.message).isEqualTo("Baby name already exists")
    }

    @Test
    fun `uniqueValue falls back to default message when none provided`() {
        val validator = FormValidations.uniqueValue(listOf("Sally"))

        val result = validator("Sally")

        assertThat(result?.message).isEqualTo(ValidationMessages.DUPLICATE)
    }

    @Test
    fun `uniqueValue returns null for empty existing list`() {
        val validator = FormValidations.uniqueValue(emptyList())

        val result = validator("Sally")

        assertThat(result).isNull()
    }

    // -------------------------------------------------------------------------
    // rangeWarningValidator (Balance Health parity — advisory, non-blocking)
    // -------------------------------------------------------------------------

    @Test
    fun `rangeWarningValidator returns null inside the typical range`() {
        val validator = FormValidations.rangeWarningValidator(60, 250)

        assertThat(validator("120")).isNull()
        assertThat(validator("60")).isNull()
        assertThat(validator("250")).isNull()
    }

    @Test
    fun `rangeWarningValidator warns below and above the range with WARNING severity`() {
        val validator = FormValidations.rangeWarningValidator(60, 250)

        val low = validator("40")
        val high = validator("300")

        assertThat(low?.severity).isEqualTo(ValidationSeverity.WARNING)
        assertThat(high?.severity).isEqualTo(ValidationSeverity.WARNING)
        assertThat(low?.message).contains("60-250")
    }

    @Test
    fun `rangeWarningValidator ignores blank and non-numeric values`() {
        val validator = FormValidations.rangeWarningValidator(60, 250)

        assertThat(validator("")).isNull()
        assertThat(validator("abc")).isNull()
    }

    // -------------------------------------------------------------------------
    // hardMaxValidator (Balance Health 500 hard cap — blocking)
    // -------------------------------------------------------------------------

    @Test
    fun `hardMaxValidator allows values up to the cap`() {
        val validator = FormValidations.hardMaxValidator(500)

        assertThat(validator("500")).isNull()
        assertThat(validator("300")).isNull()
    }

    @Test
    fun `hardMaxValidator blocks above the cap with ERROR severity`() {
        val validator = FormValidations.hardMaxValidator(500)

        val result = validator("501")

        assertThat(result).isNotNull()
        assertThat(result?.severity).isEqualTo(ValidationSeverity.ERROR)
        assertThat(result?.type).isEqualTo(ValidationType.MAX_LIMIT)
    }

    @Test
    fun `hardMaxValidator rejects non-numeric and passes blank to required`() {
        val validator = FormValidations.hardMaxValidator(500)

        assertThat(validator("12x")?.type).isEqualTo(ValidationType.NOT_IN_RANGE)
        assertThat(validator("")).isNull()
    }

    // -------------------------------------------------------------------------
    // decimalRangeValidator (Smart Baby parity — exclusive decimal bounds)
    // -------------------------------------------------------------------------

    @Test
    fun `decimalRangeValidator accepts a one-decimal value inside the range`() {
        val validator = FormValidations.decimalRangeValidator(0, 16) // oz: 0.1..15.9

        assertThat(validator("15.9")).isNull()
        assertThat(validator("0.1")).isNull()
        assertThat(validator("7.5")).isNull()
    }

    @Test
    fun `decimalRangeValidator rejects values at or beyond the exclusive bounds`() {
        val validator = FormValidations.decimalRangeValidator(0, 16)

        assertThat(validator("0")?.type).isEqualTo(ValidationType.GREATER)
        assertThat(validator("16")?.type).isEqualTo(ValidationType.LESSER)
        assertThat(validator("20")?.type).isEqualTo(ValidationType.LESSER)
    }

    @Test
    fun `decimalRangeValidator treats blank as valid and non-numeric as error`() {
        val validator = FormValidations.decimalRangeValidator(0, 16)

        assertThat(validator("")).isNull()
        assertThat(validator("1.2.3")?.type).isEqualTo(ValidationType.NOT_IN_RANGE)
    }
}
