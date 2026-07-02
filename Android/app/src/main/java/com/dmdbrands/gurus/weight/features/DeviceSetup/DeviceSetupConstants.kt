package com.dmdbrands.gurus.weight.features.DeviceSetup

/**
 * Shared constants for Scale Setup flows (Bluetooth and AppSync).
 */
object DeviceSetupConstants {
  /** Delay in ms after saving a scale or completing a step before navigating (used when not waiting for DB). */
  const val DELAY_AFTER_SAVE_MS = 1000L

  /**
   * Max time in ms to wait for a saved scale to appear in pairedScales before navigating.
   * Ensures DB/flow updates are visible before leaving the setup screen.
   */
  const val WAIT_FOR_SCALE_IN_LIST_MS = 5000L

  /** Error code for Bluetooth wake-up / pairing failure. */
  const val ERROR_WAKEUP_001 = "WAKEUP_001"

  /** Error code for Bluetooth wake-up exception. */
  const val ERROR_WAKEUP_002 = "WAKEUP_002"

  /** Error code for measurement collection failure. */
  const val ERROR_MEASUREMENT_002 = "MEASUREMENT_002"
}
