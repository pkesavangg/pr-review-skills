package com.dmdbrands.gurus.weight.features.common.enums

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Unit tests for [DeviceSetupType] product-classification predicates used to decide which
 * History empty state to show per product (MOB-1221).
 */
class DeviceSetupTypeTest {

    @Test
    fun `isWeightScale is true for weight scale setup types only`() {
        DeviceSetupType.weightScaleTypes.forEach {
            assertThat(DeviceSetupType.isWeightScale(it.value)).isTrue()
        }
        assertThat(DeviceSetupType.isWeightScale(DeviceSetupType.BpmBluetooth.value)).isFalse()
        assertThat(DeviceSetupType.isWeightScale(DeviceSetupType.BpmA6Bluetooth.value)).isFalse()
        assertThat(DeviceSetupType.isWeightScale(DeviceSetupType.BabyScale.value)).isFalse()
    }

    @Test
    fun `isBloodPressure is true for BPM setup types only`() {
        assertThat(DeviceSetupType.isBloodPressure(DeviceSetupType.BpmBluetooth.value)).isTrue()
        assertThat(DeviceSetupType.isBloodPressure(DeviceSetupType.BpmA6Bluetooth.value)).isTrue()

        assertThat(DeviceSetupType.isBloodPressure(DeviceSetupType.BabyScale.value)).isFalse()
        DeviceSetupType.weightScaleTypes.forEach {
            assertThat(DeviceSetupType.isBloodPressure(it.value)).isFalse()
        }
    }

    @Test
    fun `isBabyScale is true for baby scale setup type only`() {
        assertThat(DeviceSetupType.isBabyScale(DeviceSetupType.BabyScale.value)).isTrue()

        assertThat(DeviceSetupType.isBabyScale(DeviceSetupType.BpmBluetooth.value)).isFalse()
        assertThat(DeviceSetupType.isBabyScale(DeviceSetupType.BpmA6Bluetooth.value)).isFalse()
        DeviceSetupType.weightScaleTypes.forEach {
            assertThat(DeviceSetupType.isBabyScale(it.value)).isFalse()
        }
    }

    @Test
    fun `predicates are false for null and unknown values`() {
        listOf(null, "", "unknown_type").forEach { value ->
            assertThat(DeviceSetupType.isWeightScale(value)).isFalse()
            assertThat(DeviceSetupType.isBloodPressure(value)).isFalse()
            assertThat(DeviceSetupType.isBabyScale(value)).isFalse()
        }
    }
}
