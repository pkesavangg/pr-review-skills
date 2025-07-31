package com.dmdbrands.gurus.weight.domain.model.api.auth

import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit

data class SignupRequest(
    val email: String,
    val firstName: String,
    val lastName: String,
    val gender: String,
    val zipcode: String,
    val password: String,
    val dob: String, // formatted date
    val height: Int,
    var weightUnit: WeightUnit? = null, // nullable in case goal is skipped
    val goal: GoalData? = null, // optional embedded goal object
)

data class GoalData(
    val type: String, // "maintain", "gain", or "lose"
    val goalWeight: Float,
    val initialWeight: Float,
)
