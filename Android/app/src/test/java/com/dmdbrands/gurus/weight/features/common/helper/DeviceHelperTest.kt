package com.dmdbrands.gurus.weight.features.common.helper

import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.dmdbrands.gurus.weight.features.common.enums.DeviceSetupType
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper.SKU_0022
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper.SKU_0220
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper.SKU_0222
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper.SKU_0383
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper.SKU_0603
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper.SKU_0604
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper.SKU_0634
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper.SKU_0636
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper.SKU_0639
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper.SKU_0661
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper.SKU_0663
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper.SKU_0664
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper.SKU_0665
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper.SKU_0667
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
    fun `isBpmDevice returns true for BPM variant SKUs`() {
        assertThat(DeviceHelper.isBpmDevice(SKU_0664)).isTrue()
        assertThat(DeviceHelper.isBpmDevice(SKU_0639)).isTrue()
        assertThat(DeviceHelper.isBpmDevice(SKU_0665)).isTrue()
        assertThat(DeviceHelper.isBpmDevice(SKU_0667)).isTrue()
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

    @Test
    fun `primaryBpmSku maps variant SKUs to their primaries`() {
        assertThat(DeviceHelper.primaryBpmSku(SKU_0664)).isEqualTo(SKU_0604)
        assertThat(DeviceHelper.primaryBpmSku(SKU_0639)).isEqualTo(SKU_0636)
        assertThat(DeviceHelper.primaryBpmSku(SKU_0665)).isEqualTo(SKU_0663)
        assertThat(DeviceHelper.primaryBpmSku(SKU_0667)).isEqualTo(SKU_0661)
    }

    @Test
    fun `primaryBpmSku returns input for primary and non-BPM SKUs`() {
        assertThat(DeviceHelper.primaryBpmSku(SKU_0603)).isEqualTo(SKU_0603)
        assertThat(DeviceHelper.primaryBpmSku(SKU_0604)).isEqualTo(SKU_0604)
        assertThat(DeviceHelper.primaryBpmSku("0375")).isEqualTo("0375")
    }

    @Test
    fun `mapSkuForDisplay normalizes BPM variants to primary`() {
        assertThat(DeviceHelper.mapSkuForDisplay(SKU_0664)).isEqualTo(SKU_0604)
        assertThat(DeviceHelper.mapSkuForDisplay(SKU_0639)).isEqualTo(SKU_0636)
        assertThat(DeviceHelper.mapSkuForDisplay(SKU_0665)).isEqualTo(SKU_0663)
        assertThat(DeviceHelper.mapSkuForDisplay(SKU_0667)).isEqualTo(SKU_0661)
    }

    @Test
    fun `mapSkuForDisplay preserves legacy 0022 to 0383 mapping`() {
        assertThat(DeviceHelper.mapSkuForDisplay(SKU_0022)).isEqualTo(SKU_0383)
    }

    @Test
    fun `mapSkuForDisplay passes non-mapped SKUs through unchanged`() {
        assertThat(DeviceHelper.mapSkuForDisplay("0375")).isEqualTo("0375")
        assertThat(DeviceHelper.mapSkuForDisplay(SKU_0603)).isEqualTo(SKU_0603)
    }

    @Test
    fun `bpmListModelLabel returns grouped label for paired primaries`() {
        assertThat(DeviceHelper.bpmListModelLabel(SKU_0604)).isEqualTo("0604/0664")
        assertThat(DeviceHelper.bpmListModelLabel(SKU_0636)).isEqualTo("0636/0639")
        assertThat(DeviceHelper.bpmListModelLabel(SKU_0663)).isEqualTo("0663/0665")
        assertThat(DeviceHelper.bpmListModelLabel(SKU_0661)).isEqualTo("0661/0667")
    }

    @Test
    fun `bpmListModelLabel returns single SKU for unpaired primaries`() {
        assertThat(DeviceHelper.bpmListModelLabel(SKU_0603)).isEqualTo(SKU_0603)
        assertThat(DeviceHelper.bpmListModelLabel(SKU_0634)).isEqualTo(SKU_0634)
    }

    // ── primarySku — resolves BPM and baby-scale alternates to their primary ──

    @Test
    fun `primarySku maps baby alternate 0222 to primary 0220`() {
        assertThat(DeviceHelper.primarySku(SKU_0222)).isEqualTo(SKU_0220)
    }

    @Test
    fun `primarySku maps BPM variants to their primary`() {
        assertThat(DeviceHelper.primarySku(SKU_0664)).isEqualTo(SKU_0604)
        assertThat(DeviceHelper.primarySku(SKU_0667)).isEqualTo(SKU_0661)
    }

    @Test
    fun `primarySku passes primary and unrelated SKUs through unchanged`() {
        assertThat(DeviceHelper.primarySku(SKU_0220)).isEqualTo(SKU_0220)
        assertThat(DeviceHelper.primarySku(SKU_0604)).isEqualTo(SKU_0604)
        assertThat(DeviceHelper.primarySku("0375")).isEqualTo("0375")
    }

    // ── babyScaleListModelLabel — grouped baby pair label ──

    @Test
    fun `babyScaleListModelLabel groups the 0220 pair`() {
        assertThat(DeviceHelper.babyScaleListModelLabel(SKU_0220)).isEqualTo("0220/0222")
    }

    @Test
    fun `babyScaleListModelLabel passes non-primary SKUs through`() {
        assertThat(DeviceHelper.babyScaleListModelLabel(SKU_0222)).isEqualTo(SKU_0222)
        assertThat(DeviceHelper.babyScaleListModelLabel("0375")).isEqualTo("0375")
    }

    // ── listModelLabel — unified grouped label for list + setup header ──

    @Test
    fun `listModelLabel groups baby pair from either SKU`() {
        assertThat(DeviceHelper.listModelLabel(SKU_0220)).isEqualTo("0220/0222")
        assertThat(DeviceHelper.listModelLabel(SKU_0222)).isEqualTo("0220/0222")
    }

    @Test
    fun `listModelLabel groups BPM pairs from either SKU`() {
        assertThat(DeviceHelper.listModelLabel(SKU_0604)).isEqualTo("0604/0664")
        assertThat(DeviceHelper.listModelLabel(SKU_0664)).isEqualTo("0604/0664")
        assertThat(DeviceHelper.listModelLabel(SKU_0636)).isEqualTo("0636/0639")
        assertThat(DeviceHelper.listModelLabel(SKU_0663)).isEqualTo("0663/0665")
        assertThat(DeviceHelper.listModelLabel(SKU_0661)).isEqualTo("0661/0667")
        assertThat(DeviceHelper.listModelLabel(SKU_0667)).isEqualTo("0661/0667")
    }

    @Test
    fun `listModelLabel returns single label for unpaired BPM`() {
        assertThat(DeviceHelper.listModelLabel(SKU_0603)).isEqualTo(SKU_0603)
        assertThat(DeviceHelper.listModelLabel(SKU_0634)).isEqualTo(SKU_0634)
    }

    @Test
    fun `listModelLabel preserves legacy 0022 to 0383 and passes weight SKUs through`() {
        assertThat(DeviceHelper.listModelLabel(SKU_0022)).isEqualTo(SKU_0383)
        assertThat(DeviceHelper.listModelLabel("0375")).isEqualTo("0375")
    }

    @Test
    fun `DeviceDataHelper findScaleInfoBySku resolves variant SKUs to primary entry`() {
        val info = DeviceDataHelper.findScaleInfoBySku(SKU_0664)

        assertThat(info).isNotNull()
        // 0664 resolves to the 0604 catalog entry for lookup, but the original
        // variant SKU is preserved on the returned DeviceModelInfo. 0604 is a BPM, so its
        // catalog setupType is BpmBluetooth.
        assertThat(info?.sku).isEqualTo(SKU_0664)
        assertThat(info?.setupType).isEqualTo(DeviceSetupType.BpmBluetooth)
    }

    // ── productTypeForSku — device SKU → account ProductType (MOB-596) ──

    @Test
    fun `productTypeForSku maps baby scale SKUs to BABY`() {
        assertThat(DeviceHelper.productTypeForSku(SKU_0220)).isEqualTo(ProductType.BABY)
        assertThat(DeviceHelper.productTypeForSku(SKU_0222)).isEqualTo(ProductType.BABY)
    }

    @Test
    fun `productTypeForSku maps BPM SKUs to BLOOD_PRESSURE`() {
        assertThat(DeviceHelper.productTypeForSku(SKU_0603)).isEqualTo(ProductType.BLOOD_PRESSURE)
        assertThat(DeviceHelper.productTypeForSku(SKU_0604)).isEqualTo(ProductType.BLOOD_PRESSURE)
        assertThat(DeviceHelper.productTypeForSku(SKU_0634)).isEqualTo(ProductType.BLOOD_PRESSURE)
        assertThat(DeviceHelper.productTypeForSku(SKU_0636)).isEqualTo(ProductType.BLOOD_PRESSURE)
        assertThat(DeviceHelper.productTypeForSku(SKU_0661)).isEqualTo(ProductType.BLOOD_PRESSURE)
        assertThat(DeviceHelper.productTypeForSku(SKU_0663)).isEqualTo(ProductType.BLOOD_PRESSURE)
    }

    @Test
    fun `productTypeForSku defaults non-baby non-BPM SKUs to MY_WEIGHT`() {
        assertThat(DeviceHelper.productTypeForSku(SKU_0383)).isEqualTo(ProductType.MY_WEIGHT)
        assertThat(DeviceHelper.productTypeForSku("0375")).isEqualTo(ProductType.MY_WEIGHT)
        assertThat(DeviceHelper.productTypeForSku("0412")).isEqualTo(ProductType.MY_WEIGHT)
    }
}
