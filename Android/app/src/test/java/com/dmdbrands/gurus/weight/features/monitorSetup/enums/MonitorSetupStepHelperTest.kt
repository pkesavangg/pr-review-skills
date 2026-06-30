package com.dmdbrands.gurus.weight.features.monitorSetup.enums

import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.MonitorSetupStep
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.MonitorSetupStepHelper
import com.dmdbrands.gurus.weight.features.common.enums.ScaleSetupType
import com.greatergoods.ggbluetoothsdk.external.enums.GGDeviceProtocolType
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class MonitorSetupStepHelperTest {

  // ── isA6Sku ──

  @Test
  fun `isA6Sku returns true for 0661`() {
    assertThat(MonitorSetupStepHelper.isA6Sku("0661")).isTrue()
  }

  @Test
  fun `isA6Sku returns true for 0663`() {
    assertThat(MonitorSetupStepHelper.isA6Sku("0663")).isTrue()
  }

  @Test
  fun `isA6Sku returns false for A3 SKUs`() {
    for (sku in listOf("0603", "0604", "0634", "0636")) {
      assertThat(MonitorSetupStepHelper.isA6Sku(sku)).isFalse()
    }
  }

  @Test
  fun `isA6Sku returns false for unknown SKU`() {
    assertThat(MonitorSetupStepHelper.isA6Sku("9999")).isFalse()
  }

  // ── protocolForSku ──

  @Test
  fun `protocolForSku returns A6 protocol for A6 SKUs`() {
    val expected = GGDeviceProtocolType.GG_DEVICE_PROTOCOL_A6.value
    assertThat(MonitorSetupStepHelper.protocolForSku("0661")).isEqualTo(expected)
    assertThat(MonitorSetupStepHelper.protocolForSku("0663")).isEqualTo(expected)
  }

  @Test
  fun `protocolForSku returns A3 protocol for A3 SKUs`() {
    val expected = GGDeviceProtocolType.GG_DEVICE_PROTOCOL_A3.value
    for (sku in listOf("0603", "0604", "0634", "0636")) {
      assertThat(MonitorSetupStepHelper.protocolForSku(sku)).isEqualTo(expected)
    }
  }

  // ── setupTypeForSku ──

  @Test
  fun `setupTypeForSku returns BpmA6Bluetooth for A6 SKUs`() {
    assertThat(MonitorSetupStepHelper.setupTypeForSku("0661")).isEqualTo(ScaleSetupType.BpmA6Bluetooth)
    assertThat(MonitorSetupStepHelper.setupTypeForSku("0663")).isEqualTo(ScaleSetupType.BpmA6Bluetooth)
  }

  @Test
  fun `setupTypeForSku returns BpmBluetooth for A3 SKUs`() {
    for (sku in listOf("0603", "0604", "0634", "0636")) {
      assertThat(MonitorSetupStepHelper.setupTypeForSku(sku)).isEqualTo(ScaleSetupType.BpmBluetooth)
    }
  }

  // ── companionScaleSku ──

  @Test
  fun `companionScaleSku returns 0667 for 0661`() {
    assertThat(MonitorSetupStepHelper.companionScaleSku("0661")).isEqualTo("0667")
  }

  @Test
  fun `companionScaleSku returns 0665 for 0663`() {
    assertThat(MonitorSetupStepHelper.companionScaleSku("0663")).isEqualTo("0665")
  }

  @Test
  fun `companionScaleSku returns null for A3 SKUs`() {
    for (sku in listOf("0603", "0604", "0634", "0636")) {
      assertThat(MonitorSetupStepHelper.companionScaleSku(sku)).isNull()
    }
  }

  // ── stepsForSku — A3 SKUs ──

  @Test
  fun `stepsForSku 0636 includes POWER_SWITCH and excludes MONITOR_OFF`() {
    val steps = MonitorSetupStepHelper.stepsForSku("0636")
    assertThat(steps).contains(MonitorSetupStep.POWER_SWITCH)
    assertThat(steps).doesNotContain(MonitorSetupStep.MONITOR_OFF)
  }

  @Test
  fun `stepsForSku 0604 excludes both POWER_SWITCH and MONITOR_OFF`() {
    val steps = MonitorSetupStepHelper.stepsForSku("0604")
    assertThat(steps).doesNotContain(MonitorSetupStep.POWER_SWITCH)
    assertThat(steps).doesNotContain(MonitorSetupStep.MONITOR_OFF)
  }

  @Test
  fun `stepsForSku 0603 includes MONITOR_OFF and excludes POWER_SWITCH`() {
    val steps = MonitorSetupStepHelper.stepsForSku("0603")
    assertThat(steps).contains(MonitorSetupStep.MONITOR_OFF)
    assertThat(steps).doesNotContain(MonitorSetupStep.POWER_SWITCH)
  }

  @Test
  fun `stepsForSku 0634 includes MONITOR_OFF and excludes POWER_SWITCH`() {
    val steps = MonitorSetupStepHelper.stepsForSku("0634")
    assertThat(steps).contains(MonitorSetupStep.MONITOR_OFF)
    assertThat(steps).doesNotContain(MonitorSetupStep.POWER_SWITCH)
  }

  @Test
  fun `stepsForSku unknown SKU defaults to MONITOR_OFF pattern`() {
    val steps = MonitorSetupStepHelper.stepsForSku("9999")
    assertThat(steps).contains(MonitorSetupStep.MONITOR_OFF)
    assertThat(steps).doesNotContain(MonitorSetupStep.POWER_SWITCH)
  }

  // ── stepsForSku — A6 SKUs ──
  // A6 monitors pair their companion scale separately via Add Device (not in the wizard), so every
  // monitor flow goes pair → success → tutorial with no in-wizard scale steps. (MOB-596)

  @Test
  fun `stepsForSku 0661 goes straight from SUCCESS_SCREEN to INSTRUCTION_CUFF`() {
    val steps = MonitorSetupStepHelper.stepsForSku("0661")
    val successIndex = steps.indexOf(MonitorSetupStep.SUCCESS_SCREEN)
    assertThat(successIndex).isAtLeast(0)
    assertThat(steps[successIndex + 1]).isEqualTo(MonitorSetupStep.INSTRUCTION_CUFF)
  }

  @Test
  fun `stepsForSku 0661 excludes MONITOR_OFF and POWER_SWITCH`() {
    val steps = MonitorSetupStepHelper.stepsForSku("0661")
    assertThat(steps).doesNotContain(MonitorSetupStep.MONITOR_OFF)
    assertThat(steps).doesNotContain(MonitorSetupStep.POWER_SWITCH)
  }

  @Test
  fun `stepsForSku 0663 includes MONITOR_OFF and goes from SUCCESS_SCREEN to INSTRUCTION_CUFF`() {
    val steps = MonitorSetupStepHelper.stepsForSku("0663")
    assertThat(steps).contains(MonitorSetupStep.MONITOR_OFF)
    val successIndex = steps.indexOf(MonitorSetupStep.SUCCESS_SCREEN)
    assertThat(successIndex).isAtLeast(0)
    assertThat(steps[successIndex + 1]).isEqualTo(MonitorSetupStep.INSTRUCTION_CUFF)
  }

  @Test
  fun `stepsForSku 0663 excludes POWER_SWITCH`() {
    val steps = MonitorSetupStepHelper.stepsForSku("0663")
    assertThat(steps).doesNotContain(MonitorSetupStep.POWER_SWITCH)
  }

  // ── stepsForSku — structure ──

  @Test
  fun `all SKU variants start with MONITOR_DETAIL and end with SETUP_COMPLETED`() {
    for (sku in listOf("0603", "0604", "0634", "0636", "0661", "0663")) {
      val steps = MonitorSetupStepHelper.stepsForSku(sku)
      assertThat(steps.first()).isEqualTo(MonitorSetupStep.MONITOR_DETAIL)
      assertThat(steps.last()).isEqualTo(MonitorSetupStep.SETUP_COMPLETED)
    }
  }

  @Test
  fun `all SKU variants include common steps`() {
    val commonSteps = listOf(
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
    for (sku in listOf("0603", "0604", "0634", "0636", "0661", "0663")) {
      val steps = MonitorSetupStepHelper.stepsForSku(sku)
      assertThat(steps).containsAtLeastElementsIn(commonSteps)
    }
  }

  // ── stepsForSku — step counts ──

  @Test
  fun `stepsForSku 0636 has 12 steps`() {
    assertThat(MonitorSetupStepHelper.stepsForSku("0636")).hasSize(12)
  }

  @Test
  fun `stepsForSku 0604 has 11 steps`() {
    assertThat(MonitorSetupStepHelper.stepsForSku("0604")).hasSize(11)
  }

  @Test
  fun `stepsForSku 0603 has 12 steps`() {
    assertThat(MonitorSetupStepHelper.stepsForSku("0603")).hasSize(12)
  }

  @Test
  fun `stepsForSku 0661 has 11 steps`() {
    // 0661 dropped SCALE_INTRO + SCALE_PAIRING_INSTRUCTION; companion scale is paired separately. (MOB-596)
    assertThat(MonitorSetupStepHelper.stepsForSku("0661")).hasSize(11)
  }

  @Test
  fun `stepsForSku 0663 has 12 steps`() {
    // 0663 = 0661 flow + MONITOR_OFF; also dropped the two companion-scale steps. (MOB-596)
    assertThat(MonitorSetupStepHelper.stepsForSku("0663")).hasSize(12)
  }
}
