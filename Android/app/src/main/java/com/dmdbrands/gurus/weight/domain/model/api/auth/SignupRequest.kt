package com.dmdbrands.gurus.weight.domain.model.api.auth

data class SignupRequest(
    val email: String,
    val firstName: String,
    val lastName: String,
    // Conditional (Phase 2 / MOB-377): gender + dob required only when productTypes
    // includes "weight" or "blood_pressure"; height only when it includes "weight".
    val gender: String? = null,
    val zipcode: String,
    val password: String,
    val dob: String? = null, // formatted date
    val height: Int? = null,
    var weightUnit: String? = null, // nullable in case goal is skipped, must be "lb" or "kg"
    val goal: GoalData? = null, // optional embedded goal object
    // Phase 2 (MOB-377): which products the account owns; required measurementUnits when "baby".
    val productTypes: List<String> = listOf("weight"),
    val measurementUnits: String? = null,
)

data class GoalData(
    val type: String, // "maintain", "gain", or "lose"
    val goalWeight: Float,
    val initialWeight: Float,
)
