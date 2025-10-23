package com.dmdbrands.gurus.weight.domain.model.api.auth

data class SignupRequest(
    val email: String,
    val firstName: String,
    val lastName: String,
    val gender: String,
    val zipcode: String,
    val password: String,
    val dob: String, // formatted date
    val height: Int,
    var weightUnit: String? = null, // nullable in case goal is skipped, must be "lb" or "kg"
    val goal: GoalData? = null, // optional embedded goal object
)

data class GoalData(
    val type: String, // "maintain", "gain", or "lose"
    val goalWeight: Float,
    val initialWeight: Float,
)
