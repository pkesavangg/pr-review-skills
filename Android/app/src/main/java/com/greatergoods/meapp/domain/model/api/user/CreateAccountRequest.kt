package com.greatergoods.meapp.domain.model.api.user

import com.greatergoods.meapp.domain.model.common.WeightUnit

data class CreateAccountRequest(
    val email: String, // Required, pattern, max length 100
    val firstName: String, // Required, no whitespace, max length 100
    val lastName: String, // Required, no whitespace, max length 100
    val gender: String, // Required
    val zipcode: String, // Required, no whitespace, max length 20
    val password: String, // Required, min length 6, max length 50
    val dob: String, // Required, format: YYYY-MM-DD
    val height: Int, // Required, in mm

    // Optional fields
    val goalType: String? = null, // Optional, defaults to 'losegain'
    val weight: Double? = null, // Optional, required only if goalType is 'losegain'
    val goalWeight: Double? = null, // Optional, required only if goalType is 'losegain'
    val weightUnit: WeightUnit = WeightUnit.KG // Required
)
