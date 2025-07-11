package com.greatergoods.meapp.domain.model.permission

/**
 * Object containing permission state constants that map to GGPermissionState.
 */
object PermissionState {
  /** Permission is granted/enabled */
  const val ENABLED = "ENABLED"

  /** Permission status not determined */
  const val NOT_DETERMINED = "NOT_DETERMINED"

  /** Permission is denied/disabled */
  const val DISABLED = "DISABLED"

  /** Location permission granted but only approximate */
  const val APPROX_LOCATION = "APPROX_LOCATION"

  /** Permission has not been requested yet */
  const val NOT_REQUESTED = "NOT_REQUESTED"

  /** Permission is permanently denied by user */
  const val PERMANENTLY_DENIED = "PERMANENTLY_DENIED"
}
