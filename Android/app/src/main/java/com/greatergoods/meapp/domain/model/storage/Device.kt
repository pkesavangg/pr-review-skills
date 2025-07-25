package com.greatergoods.meapp.domain.model.storage

import com.dmdbrands.library.ggbluetooth.enums.GGAppType
import com.dmdbrands.library.ggbluetooth.model.GGDeviceDetail
import com.greatergoods.blewrapper.GGCacheDevice
import com.greatergoods.meapp.features.common.helper.DeviceHelper.getSKU
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Calendar
import java.util.UUID
import kotlin.random.Random

enum class BLEStatus {
  CONNECTED, DISCONNECTED
}

data class Device(
  val id: String = UUID.randomUUID().toString(),
  val device: GGDeviceDetail? = null,
  val connectionStatus: BLEStatus = BLEStatus.DISCONNECTED,
  val nickname: String = device?.deviceName ?: "",
  val deviceType: String? = null,
  val alreadyPaired: Boolean = false,
  val userNumber: Int? = 0,
  val hasServerID: Boolean = false,
  val createdAt: String? = Calendar.getInstance().timeInMillis.toString(),
  val sku: String? = null,
  val isWeighOnlyModeEnabledByOthers: Boolean = false,
  val token: String? = null,
  val preferences: Preferences? = null,
  val isDeleted: Boolean = false,
  val isSynced: Boolean = false
) : GGCacheDevice {

  fun getAppType(): String {
    return GGAppType.WEIGHT_GURUS
  }

  fun hasNumericUsers(): Boolean {
    return getAppType() == GGAppType.BALANCE_HEALTH
  }

  fun getSKU(): String {
    return sku ?: device?.getSKU() ?: "0375"
  }

  companion object {
    fun fromDevice(device: GGDeviceDetail, deviceType: String): Device {
      val preferences = if (device.getSKU() == "0412") {
        Preferences(shouldMeasureImpedance = device.impedanceSwitchState)
      } else null
      return Device(
        device = device,
        hasServerID = false,
        isWeighOnlyModeEnabledByOthers = false,
        preferences = preferences,
        deviceType = deviceType,
      )
    }
  }
}

@Serializable
data class Preferences(
  val id: String = Random.nextLong().toString(),
  @SerialName("tzOffset") val tzOffset: Int? = null,
  @SerialName("timeFormat") val timeFormat: String? = null,
  @SerialName("displayName") val displayName: String? = null,
  @SerialName("displayMetrics") val displayMetrics: List<String>? = null,
  @SerialName("shouldMeasurePulse") val shouldMeasurePulse: Boolean? = null,
  @SerialName("shouldMeasureImpedance") val shouldMeasureImpedance: Boolean? = null,
  @SerialName("shouldFactoryReset") val shouldFactoryReset: Boolean? = null,
  @SerialName("wifiFotaScheduleTime") val wifiFotaScheduleTime: Long? = null,
  val isSynced: Boolean = false
)
