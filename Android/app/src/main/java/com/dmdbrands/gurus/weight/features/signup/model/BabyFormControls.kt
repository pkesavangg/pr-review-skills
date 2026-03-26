package com.dmdbrands.gurus.weight.features.signup.model

import com.dmdbrands.gurus.weight.features.common.components.DateTimeValue
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.common.helper.form.FormValidations
import com.dmdbrands.gurus.weight.features.common.helper.form.ValidationError
import com.dmdbrands.gurus.weight.features.common.helper.form.ValidationMessages
import com.dmdbrands.gurus.weight.features.common.helper.form.ValidationType

data class BabyFormControls(
    val name: FormControl<String>,
    val birthday: FormControl<DateTimeValue>,
    val biologicalSex: FormControl<String>,
    val birthLength: FormControl<String>,
    val birthWeight: FormControl<String>,
) {
    companion object {
        fun create(): BabyFormControls = BabyFormControls(
            name = FormControl.create(
                initialValue = "",
                validators = listOf(FormValidations.required()),
            ),
            birthday = FormControl.create(
                initialValue = DateTimeValue.Date(System.currentTimeMillis()),
                validators = listOf(FormValidations.required()),
            ),
            biologicalSex = FormControl.create(
                initialValue = "",
                validators = listOf(FormValidations.required()),
            ),
            birthLength = FormControl.create(
                initialValue = "",
                validators = listOf(optionalNumericValidator()),
            ),
            birthWeight = FormControl.create(
                initialValue = "",
                validators = listOf(optionalNumericValidator()),
            ),
        )

        private fun optionalNumericValidator(): (String) -> ValidationError? = { value ->
            if (value.isBlank()) {
                null
            } else {
                val parsed = value.toDoubleOrNull()
                if (parsed == null || parsed <= 0) {
                    ValidationError(ValidationType.NOT_IN_RANGE, ValidationMessages.INVALID_NUMBER)
                } else {
                    null
                }
            }
        }
    }
}
