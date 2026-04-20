package com.dmdbrands.gurus.weight.features.common.helper

import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper.SKU_0220
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper.SKU_0222
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper.SKU_0603
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper.SKU_0634
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper.SKU_0661
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper.SKU_0663
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class DeviceHelperTest {

    @Test
    fun `isBpmDevice returns true for all BPM SKUs`() {
        assertThat(DeviceHelper.isBpmDevice(SKU_0663)).isTrue()
        assertThat(DeviceHelper.isBpmDevice(SKU_0661)).isTrue()
        assertThat(DeviceHelper.isBpmDevice(SKU_0634)).isTrue()
        assertThat(DeviceHelper.isBpmDevice(SKU_0603)).isTrue()
    }

    @Test
    fun `isBpmDevice returns false for non-BPM SKUs`() {
        assertThat(DeviceHelper.isBpmDevice("0412")).isFalse()
        assertThat(DeviceHelper.isBpmDevice(SKU_0220)).isFalse()
        assertThat(DeviceHelper.isBpmDevice("0375")).isFalse()
    }

    @Test
    fun `isBpmDevice returns false for null SKU`() {
        assertThat(DeviceHelper.isBpmDevice(null)).isFalse()
    }

    @Test
    fun `isBabyScale returns true for baby scale SKUs`() {
        assertThat(DeviceHelper.isBabyScale(SKU_0220)).isTrue()
        assertThat(DeviceHelper.isBabyScale(SKU_0222)).isTrue()
    }

    @Test
    fun `isBabyScale returns false for non-baby-scale SKUs`() {
        assertThat(DeviceHelper.isBabyScale("0412")).isFalse()
        assertThat(DeviceHelper.isBabyScale(SKU_0663)).isFalse()
        assertThat(DeviceHelper.isBabyScale("0375")).isFalse()
    }
}
