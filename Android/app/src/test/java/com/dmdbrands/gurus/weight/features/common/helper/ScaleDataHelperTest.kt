package com.dmdbrands.gurus.weight.features.common.helper

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ScaleDataHelperTest {

    // region formatUserDisplay

    @Test
    fun `formatUserDisplay returns empty string for null userNumber`() {
        assertThat(ScaleDataHelper.formatUserDisplay(true, null)).isEmpty()
        assertThat(ScaleDataHelper.formatUserDisplay(false, null)).isEmpty()
    }

    @Test
    fun `formatUserDisplay returns empty string for out-of-range userNumber`() {
        assertThat(ScaleDataHelper.formatUserDisplay(true, 0)).isEmpty()
        assertThat(ScaleDataHelper.formatUserDisplay(false, 0)).isEmpty()
        assertThat(ScaleDataHelper.formatUserDisplay(true, 3)).isEmpty()
        assertThat(ScaleDataHelper.formatUserDisplay(false, 3)).isEmpty()
    }

    @Test
    fun `formatUserDisplay returns numeric string when hasNumericUsers is true`() {
        assertThat(ScaleDataHelper.formatUserDisplay(true, 1)).isEqualTo("1")
        assertThat(ScaleDataHelper.formatUserDisplay(true, 2)).isEqualTo("2")
    }

    @Test
    fun `formatUserDisplay returns alphabetic label when hasNumericUsers is false`() {
        assertThat(ScaleDataHelper.formatUserDisplay(false, 1)).isEqualTo("A")
        assertThat(ScaleDataHelper.formatUserDisplay(false, 2)).isEqualTo("B")
    }

    // endregion

    // region findScaleInfoBySku

    @Test
    fun `findScaleInfoBySku returns null for null SKU`() {
        assertThat(ScaleDataHelper.findScaleInfoBySku(null)).isNull()
    }

    @Test
    fun `findScaleInfoBySku returns null for unknown SKU`() {
        assertThat(ScaleDataHelper.findScaleInfoBySku("9999")).isNull()
        assertThat(ScaleDataHelper.findScaleInfoBySku("")).isNull()
    }

    @Test
    fun `findScaleInfoBySku returns ScaleInfo for known SKU`() {
        val info = ScaleDataHelper.findScaleInfoBySku("0603")
        assertThat(info?.sku).isEqualTo("0603")
        assertThat(info?.hasNumericUsers).isTrue()
    }

    @Test
    fun `findScaleInfoBySku maps variant SKU 0022 to 0383`() {
        val info = ScaleDataHelper.findScaleInfoBySku("0022")
        assertThat(info?.sku).isEqualTo("0383")
    }

    @Test
    fun `findScaleInfoBySku returns hasNumericUsers false for 0634`() {
        val info = ScaleDataHelper.findScaleInfoBySku("0634")
        assertThat(info?.hasNumericUsers).isFalse()
    }

    // endregion
}
