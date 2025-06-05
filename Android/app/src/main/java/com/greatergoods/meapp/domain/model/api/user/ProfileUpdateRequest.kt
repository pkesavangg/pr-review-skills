package com.greatergoods.meapp.domain.model.api.user

data class ProfileUpdateRequest(
    val id: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val gender: String, // 'male' or 'female'
    val zipcode: String,
    val dob: String,
    val height: Double?,
    val weight: Double?,
    val activityLevel: String?,
    val weightUnit: String?
)
