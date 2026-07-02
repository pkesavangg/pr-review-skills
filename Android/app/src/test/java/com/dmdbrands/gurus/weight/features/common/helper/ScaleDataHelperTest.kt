package com.dmdbrands.gurus.weight.features.common.helper

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ScaleDataHelperTest {

    // region formatUserDisplay

    @Test
    fun `formatUserDisplay returns empty string for null userNumber`() {
        assertThat(DeviceDataHelper.formatUserDisplay(true, null)).isEmpty()
        assertThat(DeviceDataHelper.formatUserDisplay(false, null)).isEmpty()
    }

    @Test
    fun `formatUserDisplay returns empty string for out-of-range userNumber`() {
        assertThat(DeviceDataHelper.formatUserDisplay(true, 0)).isEmpty()
        assertThat(DeviceDataHelper.formatUserDisplay(false, 0)).isEmpty()
        assertThat(DeviceDataHelper.formatUserDisplay(true, 3)).isEmpty()
        assertThat(DeviceDataHelper.formatUserDisplay(false, 3)).isEmpty()
    }

    @Test
    fun `formatUserDisplay returns numeric string when hasNumericUsers is true`() {
        assertThat(DeviceDataHelper.formatUserDisplay(true, 1)).isEqualTo("1")
        assertThat(DeviceDataHelper.formatUserDisplay(true, 2)).isEqualTo("2")
    }

    @Test
    fun `formatUserDisplay returns alphabetic label when hasNumericUsers is false`() {
        assertThat(DeviceDataHelper.formatUserDisplay(false, 1)).isEqualTo("A")
        assertThat(DeviceDataHelper.formatUserDisplay(false, 2)).isEqualTo("B")
    }

    // endregion

    // region findScaleInfoBySku

    @Test
    fun `findScaleInfoBySku returns null for null SKU`() {
        assertThat(DeviceDataHelper.findScaleInfoBySku(null)).isNull()
    }

    @Test
    fun `findScaleInfoBySku returns null for unknown SKU`() {
        assertThat(DeviceDataHelper.findScaleInfoBySku("9999")).isNull()
        assertThat(DeviceDataHelper.findScaleInfoBySku("")).isNull()
    }

    @Test
    fun `findScaleInfoBySku returns DeviceModelInfo for known SKU`() {
        val info = DeviceDataHelper.findScaleInfoBySku("0603")
        assertThat(info?.sku).isEqualTo("0603")
        assertThat(info?.hasNumericUsers).isTrue()
    }

    @Test
    fun `findScaleInfoBySku resolves variant SKU 0022 to 0383 entry but preserves original sku`() {
        // 0022 maps to the 0383 catalog entry for lookup, but findScaleInfoBySku
        // copies the original (variant) SKU back onto the returned DeviceModelInfo.
        val info = DeviceDataHelper.findScaleInfoBySku("0022")
        assertThat(info).isNotNull()
        assertThat(info?.sku).isEqualTo("0022")
        assertThat(info?.productName).isEqualTo("Bluetooth Scale")
    }

    @Test
    fun `findScaleInfoBySku returns hasNumericUsers true for 0634`() {
        // 0634's catalog entry leaves hasNumericUsers at its default (true).
        val info = DeviceDataHelper.findScaleInfoBySku("0634")
        assertThat(info?.hasNumericUsers).isTrue()
    }

    // endregion
}
