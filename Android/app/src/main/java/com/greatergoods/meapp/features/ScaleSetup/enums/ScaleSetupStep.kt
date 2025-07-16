package com.greatergoods.meapp.features.ScaleSetup.enums

/**
 * Enum for Bluetooth + WiFi scale setup flow steps.
 */
enum class BtWifiSetupStep {
  SCALE_INFO,
  PERMISSIONS,
  WAKEUP,
  CONNECTING_BLUETOOTH,
  USER_LIMIT_REACHED,
  GATHERING_NETWORK,
  DUPLICATES_FOUND,
  AVAILABLE_WIFI_LIST,
  WIFI_PASSWORD,
  CONNECTING_WIFI,
  CUSTOMIZE_SETTINGS,
  UPDATE_SETTINGS,
  STEP_ON,
  MEASUREMENT,
  SCALE_CONNECTED,
}

enum class CUSTOMIZE_SETTINGS {
  DASHBOARD_METRICS,
  SCALE_MODE,
  SCALE_METRICS,
  SCALE_USERNAME,
}

/**
 * Enum for WiFi-only scale setup flow steps.
 */
enum class WifiScaleSetupStep {
  SCALE_INFO,
  PERMISSIONS,

  // WifiPassword step data (ssid) should be stored in state, not here
  WIFI_PASSWORD,
  SELECT_USER,
  ACTIVATE_SCALE,
  CONNECTING_SCALE,
  ERROR_GUIDE,
  TROUBLE_SHOOTING,
  SWITCH_WIFI,
  SCALE_COUNTS,
  STEP_ON,
  SCALE_CONNECTED
}

/**
 * Enum for Bluetooth-only scale setup flow steps.
 */
enum class BtScaleSetupStep {
  SCALE_INFO,
  PERMISSIONS,
  SELECT_USER,
  CONNECTING_BLUETOOTH,
  FIND_USER,
  STEP_ON,
  SETUP_FINISHED
}

/**
 * Enum for LCBT scale setup flow steps.
 */
enum class LcbtScaleSetupStep {
  SCALE_INFO,
  PERMISSIONS,
  STEP_ON,
  CONNECTING_BLUETOOTH,
  SETUP_FINISHED
}

/**
 * Enum for Appsync scale setup flow steps.
 */
enum class AppsyncScaleSetupStep {
  SCALE_INFO,
  PERMISSIONS,
  ACTIVATE_SCALE,
  ADD_INFO,
  STEP_ON,
  OPEN_CAMERA,
  SETUP_FINISHED
}

/**
 * Enum for customize settings steps.
 */
enum class CustomizeSettings(
  val value: Int,
) {
  NONE(-1),
  DASHBOARD_METRICS(0),
  SCALE_METRICS(1),
  SCALE_MODE(2),
  SCALE_USERNAME(3),
}
