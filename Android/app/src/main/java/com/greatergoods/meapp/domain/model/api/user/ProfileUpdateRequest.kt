package com.greatergoods.meapp.domain.model.api.user

data class ProfileUpdateRequest(
    val email: String,
    val firstName: String,
    val lastName: String,
    val gender: String, // 'male' or 'female'
    val zipcode: String,
    val dob: String
)
