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
  // Phase 2 (MOB-377): account measurement system. productTypes is managed via signup
  // and the dedicated endpoints, so it is not part of the profile update body.
  val measurementUnits: String? = null,
)
