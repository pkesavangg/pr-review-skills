package com.dmdbrands.gurus.weight.domain.enum

/**
 * Custom permission types that are specific to the app and not part of the core GGPermissionType enum.
 * These are used for special permission handling scenarios.
 */
enum class CustomPermissionType(val value: String) {
  /** WiFi switch permission that should be grouped under Location header instead of Network header */
  WIFI_SWITCH_LOCATION("WIFI_SWITCH_LOCATION")
}
