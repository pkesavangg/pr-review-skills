package com.dmdbrands.gurus.weight.features.help.model

import com.dmdbrands.gurus.weight.features.common.model.BABY_SCALES
import com.dmdbrands.gurus.weight.features.common.model.BPM_DEVICES
import com.dmdbrands.gurus.weight.features.common.model.SCALES
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class DeviceCatalogDataTest {

  @Test
  fun `BABY_SCALES has expected SKUs`() {
    val skus = BABY_SCALES.map { it.sku }
    assertThat(skus).containsExactly("0220", "0222")
  }

  @Test
  fun `BABY_SCALES has no duplicate SKUs`() {
    val skus = BABY_SCALES.map { it.sku }
    assertThat(skus).containsNoDuplicates()
  }

  @Test
  fun `BPM_DEVICES has expected SKUs`() {
    val skus = BPM_DEVICES.map { it.sku }
    assertThat(skus).containsExactly("0603", "0604", "0634", "0636", "0661", "0663")
  }

  @Test
  fun `BPM_DEVICES has no duplicate SKUs`() {
    val skus = BPM_DEVICES.map { it.sku }
    assertThat(skus).containsNoDuplicates()
  }

  @Test
  fun `SCALES has no duplicate SKUs`() {
    val skus = SCALES.map { it.sku }
    assertThat(skus).containsNoDuplicates()
  }

  @Test
  fun `all device lists are non-empty`() {
    assertThat(BABY_SCALES).isNotEmpty()
    assertThat(BPM_DEVICES).isNotEmpty()
    assertThat(SCALES).isNotEmpty()
  }

  @Test
  fun `BABY_SCALES is a subset of SCALES`() {
    assertThat(SCALES).containsAtLeastElementsIn(BABY_SCALES)
  }

  @Test
  fun `BPM_DEVICES is a subset of SCALES`() {
    assertThat(SCALES).containsAtLeastElementsIn(BPM_DEVICES)
  }

  @Test
  fun `no SKU overlap between BABY_SCALES and BPM_DEVICES`() {
    val babySkus = BABY_SCALES.map { it.sku }.toSet()
    val bpmSkus = BPM_DEVICES.map { it.sku }.toSet()
    assertThat(babySkus.intersect(bpmSkus)).isEmpty()
  }

  @Test
  fun `all BABY_SCALES have non-blank product names`() {
    BABY_SCALES.forEach { device ->
      assertThat(device.productName).isNotEmpty()
    }
  }

  @Test
  fun `all BPM_DEVICES have non-blank product names`() {
    BPM_DEVICES.forEach { device ->
      assertThat(device.productName).isNotEmpty()
    }
  }
}
