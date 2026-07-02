package com.dmdbrands.gurus.weight.features.DeviceSetup.enums

sealed interface DeviceSetupStep

/**
 * Enum for Bluetooth + WiFi scale setup flow steps.
 */
enum class BtWifiSetupStep : DeviceSetupStep {
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
  SETUP_FINISHED,
}

enum class CUSTOMIZE_SETTINGS {
  DASHBOARD_METRICS,
  SCALE_MODE,
  SCALE_METRICS,
  DEVICE_USERNAME,
}

/**
 * Enum for WiFi-only scale setup flow steps.
 */
enum class WifiScaleSetupStep {
  SCALE_INFO,
  PERMISSIONS,
  WIFI_PASSWORD,
  SELECT_USER,
  ACTIVATE_SCALE,
  WIFI_MODE,
  SWITCH_WIFI,
  SCALE_COUNTS,
  STEP_ON,
  SETUP_FINISHED,
  MAC_ADDRESS,
  ERROR_GUIDE,
  ERROR_CODE_SELECTED,
  TROUBLE_SHOOTING,
}

/**
 * Enum for Bluetooth-only scale setup flow steps.
 */
enum class BtScaleSetupStep : DeviceSetupStep {
  SCALE_INFO,
  PERMISSIONS,
  SELECT_USER,
  PAIRING_MODE,
  SET_DEVICE_USER,
  STEP_ON,
  SETUP_FINISHED
}

/**
 * Enum for LCBT scale setup flow steps.
 */
enum class LcbtScaleSetupStep : DeviceSetupStep {
  SCALE_INFO,
  PERMISSIONS,
  WAKEUP,
  CONNECTING_BLUETOOTH,
  SETUP_FINISHED
}

/**
 * Enum for Baby Scale setup flow steps.
 */
enum class BabyScaleSetupStep : DeviceSetupStep {
  SCALE_INFO,
  PERMISSIONS,
  WAKEUP,
  SCALE_NAME,
  PAIRED_SUCCESS,
  BABY_PROFILE_FORM,
  BABY_LIST,
  SETUP_FINISHED,
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
  NONE(0),
  DASHBOARD_METRICS(1),
  SCALE_METRICS(2),
  SCALE_MODE(3),
  DEVICE_USERNAME(4),
}
