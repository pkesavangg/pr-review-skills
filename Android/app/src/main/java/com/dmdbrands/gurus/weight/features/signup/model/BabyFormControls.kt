package com.dmdbrands.gurus.weight.features.signup.model

import com.dmdbrands.gurus.weight.features.common.components.DateTimeValue
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.common.helper.form.FormValidations
import com.dmdbrands.gurus.weight.features.signup.strings.BabySignupStrings

enum class BabyWeightUnit {
    LBS,
    LBS_OZ,
    KG,
}

data class BabyFormControls(
    val name: FormControl<String>,
    val birthday: FormControl<DateTimeValue>,
    val biologicalSex: FormControl<String>,
    val birthLength: FormControl<String>,
    val birthWeight: FormControl<String>,
    val birthWeightOz: FormControl<String>,
    val weightUnit: FormControl<BabyWeightUnit>,
) {
    companion object {
        /**
         * @param existingNames names of babies already added during this signup session
         *   (excluding the one being edited). Used to flag duplicate baby names inline.
         */
        fun create(existingNames: List<String> = emptyList()): BabyFormControls = BabyFormControls(
            name = FormControl.create(
                initialValue = "",
                validators = listOf(
                    FormValidations.required(),
                    FormValidations.uniqueValue(existingNames, BabySignupStrings.nameDuplicateError),
                ),
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
                validators = emptyList(),
            ),
            birthWeight = FormControl.create(
                initialValue = "",
                validators = emptyList(),
            ),
            birthWeightOz = FormControl.create(
                initialValue = "",
                validators = emptyList(),
            ),
            weightUnit = FormControl.create(
                initialValue = BabyWeightUnit.LBS,
                validators = emptyList(),
            ),
        )
    }
}
