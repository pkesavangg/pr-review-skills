package com.dmdbrands.gurus.weight.features.ScaleSetup.enums

import com.dmdbrands.gurus.weight.features.common.enums.ScaleSetupType
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper
import com.greatergoods.ggbluetoothsdk.external.enums.GGDeviceProtocolType

/**
 * Unified enum for BPM monitor setup flow steps.
 * Covers both A3 (0603, 0604, 0634, 0636) and A6 (0661, 0663) protocols.
 * Step lists are built per SKU via [MonitorSetupStepHelper.stepsForSku].
 */
enum class MonitorSetupStep : ScaleSetupStep {
  MONITOR_DETAIL,
  PERMISSIONS,
  USER_SELECTION,
  POWER_SWITCH,       // 0636 only
  USER_CONFIRMATION,
  MONITOR_OFF,        // 0603, 0634, 0663 only
  MEMORY_SELECTION,
  MONITOR_PAIRING,
  MONITOR_NICKNAME,
  SUCCESS_SCREEN,
  INSTRUCTION_CUFF,
  INSTRUCTION_START,
  SETUP_COMPLETED,
}

/**
 * Helper for building per-SKU step lists and resolving protocol/scale metadata.
 *
 * A3 SKUs: 0603, 0604, 0634, 0636
 * A6 SKUs: 0661, 0663 (with companion scale pairing: 0661→0667, 0663→0665)
 */
object MonitorSetupStepHelper {

  private val A6_SKUS = setOf("0661", "0663")

  fun isA6Sku(sku: String): Boolean = DeviceHelper.primaryBpmSku(sku) in A6_SKUS

  fun protocolForSku(sku: String): String =
    if (isA6Sku(sku)) GGDeviceProtocolType.GG_DEVICE_PROTOCOL_A6.value
    else GGDeviceProtocolType.GG_DEVICE_PROTOCOL_A3.value

  fun setupTypeForSku(sku: String): ScaleSetupType =
    if (isA6Sku(sku)) ScaleSetupType.BpmA6Bluetooth
    else ScaleSetupType.BpmBluetooth

  /**
   * Returns the SKU of the companion scale that pairs with the given A6 monitor SKU,
   * or null for A3 SKUs.
   */
  fun companionScaleSku(sku: String): String? = when (sku) {
    "0661" -> "0667"
    "0663" -> "0665"
    else -> null
  }

  fun stepsForSku(sku: String): List<MonitorSetupStep> = when (DeviceHelper.primaryBpmSku(sku)) {
    // A3: 0636 — uses POWER_SWITCH instead of MONITOR_OFF
    "0636" -> listOf(
      MonitorSetupStep.MONITOR_DETAIL,
      MonitorSetupStep.PERMISSIONS,
      MonitorSetupStep.USER_SELECTION,
      MonitorSetupStep.POWER_SWITCH,
      MonitorSetupStep.USER_CONFIRMATION,
      MonitorSetupStep.MEMORY_SELECTION,
      MonitorSetupStep.MONITOR_PAIRING,
      MonitorSetupStep.MONITOR_NICKNAME,
      MonitorSetupStep.SUCCESS_SCREEN,
      MonitorSetupStep.INSTRUCTION_CUFF,
      MonitorSetupStep.INSTRUCTION_START,
      MonitorSetupStep.SETUP_COMPLETED,
    )

    // A3: 0604 — no power-related step
    "0604" -> listOf(
      MonitorSetupStep.MONITOR_DETAIL,
      MonitorSetupStep.PERMISSIONS,
      MonitorSetupStep.USER_SELECTION,
      MonitorSetupStep.USER_CONFIRMATION,
      MonitorSetupStep.MEMORY_SELECTION,
      MonitorSetupStep.MONITOR_PAIRING,
      MonitorSetupStep.MONITOR_NICKNAME,
      MonitorSetupStep.SUCCESS_SCREEN,
      MonitorSetupStep.INSTRUCTION_CUFF,
      MonitorSetupStep.INSTRUCTION_START,
      MonitorSetupStep.SETUP_COMPLETED,
    )

    // A6: 0661 — no MONITOR_OFF. Companion scale is paired separately via Add Device (not in the
    // wizard), so the flow is the same as every other monitor: pair → success → tutorial. (MOB-596)
    "0661" -> listOf(
      MonitorSetupStep.MONITOR_DETAIL,
      MonitorSetupStep.PERMISSIONS,
      MonitorSetupStep.USER_SELECTION,
      MonitorSetupStep.USER_CONFIRMATION,
      MonitorSetupStep.MEMORY_SELECTION,
      MonitorSetupStep.MONITOR_PAIRING,
      MonitorSetupStep.MONITOR_NICKNAME,
      MonitorSetupStep.SUCCESS_SCREEN,
      MonitorSetupStep.INSTRUCTION_CUFF,
      MonitorSetupStep.INSTRUCTION_START,
      MonitorSetupStep.SETUP_COMPLETED,
    )

    // A6: 0663 — with MONITOR_OFF. Companion scale is paired separately via Add Device (not in the
    // wizard), so the flow matches every other monitor: pair → success → tutorial. (MOB-596)
    "0663" -> listOf(
      MonitorSetupStep.MONITOR_DETAIL,
      MonitorSetupStep.PERMISSIONS,
      MonitorSetupStep.USER_SELECTION,
      MonitorSetupStep.USER_CONFIRMATION,
      MonitorSetupStep.MONITOR_OFF,
      MonitorSetupStep.MEMORY_SELECTION,
      MonitorSetupStep.MONITOR_PAIRING,
      MonitorSetupStep.MONITOR_NICKNAME,
      MonitorSetupStep.SUCCESS_SCREEN,
      MonitorSetupStep.INSTRUCTION_CUFF,
      MonitorSetupStep.INSTRUCTION_START,
      MonitorSetupStep.SETUP_COMPLETED,
    )

    // A3 default (0603, 0634): with MONITOR_OFF, no scale steps
    else -> listOf(
      MonitorSetupStep.MONITOR_DETAIL,
      MonitorSetupStep.PERMISSIONS,
      MonitorSetupStep.USER_SELECTION,
      MonitorSetupStep.USER_CONFIRMATION,
      MonitorSetupStep.MONITOR_OFF,
      MonitorSetupStep.MEMORY_SELECTION,
      MonitorSetupStep.MONITOR_PAIRING,
      MonitorSetupStep.MONITOR_NICKNAME,
      MonitorSetupStep.SUCCESS_SCREEN,
      MonitorSetupStep.INSTRUCTION_CUFF,
      MonitorSetupStep.INSTRUCTION_START,
      MonitorSetupStep.SETUP_COMPLETED,
    )
  }
}
