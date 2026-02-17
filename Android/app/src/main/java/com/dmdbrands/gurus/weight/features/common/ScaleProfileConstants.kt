package com.dmdbrands.gurus.weight.features.common

/**
 * Constants for R4 scale profile update operations.
 * Used when pushing user profile (name, dob, height, unit, goal, etc.) to connected BT or WiFi scales.
 */
object ScaleProfileConstants {
  /**
   * Timeout in milliseconds for waiting for scale callback when updating profile.
   * If the scale does not respond within this time, the loader is dismissed and flow continues.
   */
  const val SCALE_PROFILE_UPDATE_TIMEOUT_MS = 2_000L
}
