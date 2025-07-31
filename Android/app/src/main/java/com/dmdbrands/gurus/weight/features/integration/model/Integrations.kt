package com.dmdbrands.gurus.weight.features.integration.model

// 1. Your domain model as a Kotlin data class
data class Integrations(
  val isFitbitOn: Boolean = false,
  val isGoogleFitOn: Boolean = false,
  val isMFPOn: Boolean = false,
  val isUAOn: Boolean = false,
  val isFitbitValid: Boolean = false,
  val isGoogleFitValid: Boolean = false,
  val isMFPValid: Boolean = false,
  val isUAValid: Boolean = false,
  val healthkit: Boolean = false,
  val isHealthConnectOn: Boolean = false,
)

