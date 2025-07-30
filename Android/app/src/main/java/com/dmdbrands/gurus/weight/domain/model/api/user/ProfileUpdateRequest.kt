package com.dmdbrands.gurus.weight.domain.model.api.user

data class
ProfileUpdateRequest(
  val id: String,
  val email: String,
  val firstName: String,
  val lastName: String,
  val gender: String, // 'male' or 'female'
  val zipcode: String,
  val dob: String,
  val height: Double? = null,
  val weight: Double? = null,
  val activityLevel: String? = null,
  val weightUnit: String? = null,
)
