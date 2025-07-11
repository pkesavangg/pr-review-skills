package com.greatergoods.meapp.features.ScaleSetup.enums


/**
 * Steps for Bluetooth + WiFi scale setup flow.
 */
sealed class BtWifiSetupStep {
    object ScaleInfo : BtWifiSetupStep()
    object Permissions : BtWifiSetupStep()
    object Wakeup : BtWifiSetupStep()
    object ConnectingBluetooth : BtWifiSetupStep()
    object GatheringNetwork : BtWifiSetupStep()
    object DuplicatesFound : BtWifiSetupStep()
    object AvailableWifiList : BtWifiSetupStep()
    data class WifiPassword(val ssid: String) : BtWifiSetupStep()
    object ConnectingWifi : BtWifiSetupStep()
    object CustomizeSettings : BtWifiSetupStep()
    object ViewSettings : BtWifiSetupStep()
    object UpdateSettings : BtWifiSetupStep()
    object StepOn : BtWifiSetupStep()
    object Measurement : BtWifiSetupStep()
    object ScaleConnected : BtWifiSetupStep()
}

/**
 * Steps for WiFi-only scale setup flow.
 */
sealed class WifiScaleSetupStep {
    object ScaleInfo : WifiScaleSetupStep()
    object Permissions : WifiScaleSetupStep()
    data class WifiPassword(val ssid: String) : WifiScaleSetupStep()
    object SelectUser : WifiScaleSetupStep()
    object ActivateScale : WifiScaleSetupStep()
    object ConnectingScale : WifiScaleSetupStep()
    object ErrorGuide : WifiScaleSetupStep()
    object TroubleShooting : WifiScaleSetupStep()
    object SwitchWifi : WifiScaleSetupStep()
    object ScaleCounts : WifiScaleSetupStep()
    object StepOn : WifiScaleSetupStep()
    object ScaleConnected : WifiScaleSetupStep()
}

/**
 * Steps for Bluetooth-only scale setup flow.
 */
sealed class BtScaleSetupStep {
    object ScaleInfo : BtScaleSetupStep()
    object Permissions : BtScaleSetupStep()
    object SelectUser : BtScaleSetupStep()
    object ConnectingBluetooth : BtScaleSetupStep()
    object FindUser : BtScaleSetupStep()
    object StepOn : BtScaleSetupStep()
    object SetupFinished : BtScaleSetupStep()
}

/**
 * Steps for LCBT scale setup flow.
 */
sealed class LcbtScaleSetupStep {
    object ScaleInfo : LcbtScaleSetupStep()
    object Permissions : LcbtScaleSetupStep()
    object StepOn : LcbtScaleSetupStep()
    object ConnectingBluetooth : LcbtScaleSetupStep()
    object SetupFinished : LcbtScaleSetupStep()
}

/**
 * Steps for Appsync scale setup flow.
 */
sealed class AppsyncScaleSetupStep {
    object ScaleInfo : AppsyncScaleSetupStep()
    object Permissions : AppsyncScaleSetupStep()
    object ActivateScale : AppsyncScaleSetupStep()
    object AddInfo : AppsyncScaleSetupStep()
    object StepOn : AppsyncScaleSetupStep()
    object OpenCamera : AppsyncScaleSetupStep()
    object SetupFinished : AppsyncScaleSetupStep()
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
