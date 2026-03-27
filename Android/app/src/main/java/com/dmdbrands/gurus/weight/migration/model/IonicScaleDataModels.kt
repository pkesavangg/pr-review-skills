package com.dmdbrands.gurus.weight.migration.model

import com.google.gson.annotations.SerializedName

/**
 * Data classes and enums that replicate the TypeScript Scale interface and related types for Gson parsing.
 *
 * These classes are designed to work with Gson for JSON deserialization from the Ionic app.
 * All fields are nullable to handle missing or optional properties in the JSON.
 *
 * The main classes include:
 * - IonicScale: Main scale interface
 * - IonicScaleInfo: Scale information interface
 * - IonicR4ScalePreference: R4 scale preferences
 * - IonicScaleMetaData: Scale metadata
 * - Various scale-related enums
 *
 * Usage:
 * ```kotlin
 * val gson = Gson()
 * val ionicScale = gson.fromJson(jsonString, IonicScale::class.java)
 * ```
 */

/**
 * Setup type enum for scales
 */
enum class SetupType {
  @SerializedName("wifi")
  WIFI,

  @SerializedName("bluetooth")
  BLUETOOTH,

  @SerializedName("appSync")
  APP_SYNC,

  @SerializedName("lcbt")
  LCBT,

  @SerializedName("espTouchWifi")
  ESP_TOUCH_WIFI,

  @SerializedName("btWifiR4")
  BT_WIFI_R4
}

/**
 * Scale setup type enum
 */
enum class ScaleSetupType {
  @SerializedName("wifi")
  WIFI,

  @SerializedName("bluetooth")
  BLUETOOTH,

  @SerializedName("appSync")
  APP_SYNC,

  @SerializedName("lcbt")
  LCBT,

  @SerializedName("espTouchWifi")
  ESP_TOUCH_WIFI,

  @SerializedName("btWifiR4")
  BT_WIFI_R4
}

/**
 * Scale type enum
 */
enum class ScaleType {
  @SerializedName("wifi")
  WIFI,

  @SerializedName("espTouchWifi")
  ESP_TOUCH_WIFI,

  @SerializedName("bluetooth")
  BLUETOOTH,

  @SerializedName("lcbt")
  LCBT,

  @SerializedName("lcbt scale")
  LCBT_SCALE,

  @SerializedName("bluetooth scale")
  BLUETOOTH_SCALE,

  @SerializedName("btWifiR4")
  BT_WIFI_R4,

  @SerializedName("appsync")
  APPSYNC,

  @SerializedName("appsync scale")
  APPSYNC_SCALE
}

/**
 * Scale setup error enum
 */
enum class ScaleSetupError {
  @SerializedName("none")
  NONE,

  @SerializedName("bluetoothConnectionFailed")
  BLUETOOTH_CONNECTION_FAILED,

  @SerializedName("duplicatesFound")
  DUPLICATES_FOUND,

  @SerializedName("maxUserReached")
  MAX_USER_REACHED,

  @SerializedName("noNetworkFound")
  NO_NETWORK_FOUND,

  @SerializedName("wifiConnectionFailed")
  WIFI_CONNECTION_FAILED,

  @SerializedName("updateSettingsFailed")
  UPDATE_SETTINGS_FAILED,

  @SerializedName("collectMeasurementFailed")
  COLLECT_MEASUREMENT_FAILED
}

/**
 * Scale mode enum
 */
enum class ScaleModeEnum {
  @SerializedName("metrics")
  METRICS,

  @SerializedName("weight")
  WEIGHT
}

/**
 * Scale detail loader enum
 */
enum class ScaleDetailLoader {
  @SerializedName("none")
  NONE,

  @SerializedName("loadWifiMacAddress")
  LOAD_WIFI_MAC_ADDRESS,

  @SerializedName("loadScaleUsers")
  LOAD_SCALE_USERS,

  @SerializedName("loadConnectedWifiSSID")
  LOAD_CONNECTED_WIFI_SSID
}

/**
 * Customize settings enum
 */
enum class CustomizeSettings {
  @SerializedName("none")
  NONE,

  @SerializedName("dashboardMetrics")
  DASHBOARD_METRICS,

  @SerializedName("scaleMetrics")
  SCALE_METRICS,

  @SerializedName("scaleMode")
  SCALE_MODE,

  @SerializedName("scaleUsername")
  SCALE_USERNAME
}

/**
 * ScaleInfo interface equivalent
 */
data class IonicScaleInfo(
  @SerializedName("productName") val productName: String? = null,
  @SerializedName("sku") val sku: String? = null,
  @SerializedName("imgPath") val imgPath: String? = null,
  @SerializedName("setupType") val setupType: String? = null, // Using String for flexibility
  @SerializedName("nickname") val nickname: String? = null,
  @SerializedName("bodyComp") val bodyComp: Boolean? = null,
  @SerializedName("isConnected") val isConnected: Boolean? = null,
  @SerializedName("isWifiConfigured") val isWifiConfigured: Boolean? = null
)

/**
 * ScaleWifiConfiguredInfo interface equivalent
 */
data class IonicScaleWifiConfiguredInfo(
  @SerializedName("broadcastId") val broadcastId: String? = null,
  @SerializedName("isConfigured") val isConfigured: Boolean? = null
)

/**
 * R4ScalePreference interface equivalent
 */
data class IonicR4ScalePreference(
  @SerializedName("scaleId") val scaleId: String? = null,
  @SerializedName("displayName") val displayName: String? = null,
  @SerializedName("displayMetrics") val displayMetrics: List<String>? = null,
  @SerializedName("shouldFactoryReset") val shouldFactoryReset: Boolean? = null,
  @SerializedName("shouldMeasureImpedance") val shouldMeasureImpedance: Boolean? = null,
  @SerializedName("shouldMeasurePulse") val shouldMeasurePulse: Boolean? = null,
  @SerializedName("timeFormat") val timeFormat: String? = null,
  @SerializedName("tzOffset") val tzOffset: Double? = null,
  @SerializedName("wifiFotaScheduleTime") val wifiFotaScheduleTime: Double? = null,
  @SerializedName("updatedAt") val updatedAt: String? = null,
  @SerializedName("isTemporary") val isTemporary: Boolean? = null
)

/**
 * ScaleMetaData interface equivalent
 */
data class IonicScaleMetaData(
  @SerializedName("modelNumber") val modelNumber: String? = null,
  @SerializedName("serialNumber") val serialNumber: String? = null,
  @SerializedName("firmwareRevision") val firmwareRevision: String? = null,
  @SerializedName("hardwareRevision") val hardwareRevision: String? = null,
  @SerializedName("softwareRevision") val softwareRevision: String? = null,
  @SerializedName("manufacturerName") val manufacturerName: String? = null,
  /**
   * Applicable for A3 scales only (Device MAC Address)
   */
  @SerializedName("systemId") val systemId: String? = null,
  /**
   * Applicable for R4 scales only (Wifi MAC Address)
   */
  @SerializedName("wifiMac") val wifiMac: String? = null
)

/**
 * ScaleMode interface equivalent
 */
data class IonicScaleMode(
  @SerializedName("mode") val mode: Boolean? = null,
  @SerializedName("heartRate") val heartRate: Boolean? = null
)

/**
 * DisplayNameEditInfo interface equivalent
 */
data class IonicDisplayNameEditInfo(
  @SerializedName("name") val name: String? = null,
  @SerializedName("isValid") val isValid: Boolean? = null
)

/**
 * CustomizeSettingPage interface equivalent
 */
data class IonicCustomizeSettingPage(
  @SerializedName("icon") val icon: String? = null,
  @SerializedName("title") val title: String? = null,
  @SerializedName("copy") val copy: String? = null,
  @SerializedName("isOpened") val isOpened: Boolean? = null,
  @SerializedName("shouldSave") val shouldSave: Boolean? = null,
  @SerializedName("index") val index: String? = null // Using String for flexibility
)

/**
 * Main Scale interface equivalent (extends ScaleInfo)
 *
 * This represents the complete Scale data structure from the TypeScript interface,
 * combining Scale-specific fields with inherited ScaleInfo properties.
 */
data class IonicScale(
  // ScaleInfo inherited fields
  @SerializedName("productName") val productName: String? = null,
  @SerializedName("sku") val sku: String? = null,
  @SerializedName("imgPath") val imgPath: String? = null,
  @SerializedName("setupType") val setupType: String? = null,
  @SerializedName("nickname") val nickname: String? = null,
  @SerializedName("bodyComp") val bodyComp: Boolean? = null,

  // Scale-specific fields
  @SerializedName("name") val name: String? = null,
  @SerializedName("type") val type: String? = null,
  @SerializedName("userId") val userId: String? = null,
  @SerializedName("peripheralIdentifier") val peripheralIdentifier: String? = null,
  @SerializedName("preference") val preference: IonicR4ScalePreference? = null,
  @SerializedName("metaData") val metaData: IonicScaleMetaData? = null,
  @SerializedName("userNumber") val userNumber: Int? = null,
  @SerializedName("scaleToken") val scaleToken: String? = null,
  @SerializedName("broadcastId") val broadcastId: Long? = null,
  @SerializedName("broadcastIdString") val broadcastIdString: String? = null,
  @SerializedName("latestVersion") val latestVersion: String? = null,
  @SerializedName("id") val id: String? = null,
  @SerializedName("mac") val mac: String? = null,
  @SerializedName("password") val password: Long? = null,
  @SerializedName("createdAt") val createdAt: String? = null,
  @SerializedName("isTemporary") val isTemporary: Boolean? = null,
  @SerializedName("isDeleted") val isDeleted: Boolean? = null,
  @SerializedName("isWifiConfigured") val isWifiConfigured: Boolean? = null,
  @SerializedName("isConnected") val isConnected: Boolean? = null,
  @SerializedName("isWeighOnlyModeEnabledByOthers") val isWeighOnlyModeEnabledByOthers: Boolean? = null
)

/**
 * BTScaleData interface equivalent
 */
data class IonicBTScaleData(
  @SerializedName("name") val name: String? = null,
  @SerializedName("broadcastId") val broadcastId: String? = null,
  @SerializedName("protocolType") val protocolType: String? = null,
  @SerializedName("peripheralIdentifier") val peripheralIdentifier: String? = null,
  @SerializedName("mac") val mac: String? = null,
  @SerializedName("password") val password: String? = null,
  @SerializedName("userNumber") val userNumber: Int? = null,
  @SerializedName("isConnected") val isConnected: Boolean? = null,
  @SerializedName("preference") val preference: IonicR4ScalePreference? = null
)

/**
 * Wrapper data class for devices JSON structure.
 * Handles cases where the JSON contains a "devices" array of scales.
 */
data class IonicDevices(
  @SerializedName("devices") val devices: List<IonicScale>
)

/**
 * Scale migration result specific to scale operations.
 */
sealed class ScaleMigrationResult {
  data class Success(
    val migratedScalesCount: Int,
    val migratedPreferencesCount: Int
  ) : ScaleMigrationResult() {
    override val errorMessage: String? = null
  }

  data class Failure(override val errorMessage: String) : ScaleMigrationResult()

  val isSuccess: Boolean
    get() = this is Success

  abstract val errorMessage: String?

  companion object {
    fun success(scalesCount: Int, preferencesCount: Int) = Success(scalesCount, preferencesCount)
    fun failure(message: String) = Failure(message)
  }
}
