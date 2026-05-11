package com.dmdbrands.gurus.weight.features.common.helper

import com.dmdbrands.library.ggbluetooth.model.GGDeviceDetail

object DeviceHelper {
  const val DEFAULT_SKU = "0375"
  const val SKU_0412 = "0412"
  const val SKU_0376 = "0376"
  const val SKU_0380 = "0380"
  const val SKU_0022 = "0022"
  const val SKU_0383 = "0383"
  const val SKU_0480 = "0480"
  const val SKU_0604 = "0604"
  const val SKU_0003 = "0003"
  const val SKU_0005 = "0005"
  const val SKU_0062 = "0062"
  const val SKU_0603 = "0603"
  const val SKU_0222 = "0222"
  const val SKU_0220 = "0220"
  const val SKU_0663 = "0663"
  const val SKU_0661 = "0661"
  const val SKU_0634 = "0634"
  const val SKU_0344 = "0344"

  fun GGDeviceDetail.getSKU(): String? = SKU_MAP[deviceName]

  /**
   * Maps a SKU to its display SKU for UI purposes only.
   * For example, SKU 0022 is displayed as 0383.
   * Returns null when [sku] is null so callers can decide on a UI fallback rather than
   * masquerading unknown devices as a default SKU.
   * @param sku The SKU to map for display, or null when no SKU is known
   * @return The display SKU, or null if [sku] is null
   */
  fun mapSkuForDisplay(sku: String?): String? = when (sku) {
      null -> null
      SKU_0022 -> SKU_0383
      else -> sku
  }

  private val SKU_MAP = mapOf(
    "MY_SCALE" to SKU_0480,
    "1490BT1" to SKU_0604,
    "10376B" to SKU_0376,
    "0376B" to SKU_0376,
    "376B" to SKU_0376,
    "0202B" to DEFAULT_SKU,
    "1202B" to DEFAULT_SKU,
    "202B" to DEFAULT_SKU,
    "11251B" to SKU_0380,
    "1251B" to SKU_0380,
    "01251B" to SKU_0380,
    "1270B" to SKU_0380,
    "11270B" to SKU_0380,
    "01270B" to SKU_0380,
    "gG BS 0412" to SKU_0412,
    "LS212-B" to SKU_0383,
    "gG-RPM 0022" to SKU_0022,
    "gG PulseOx 0003" to SKU_0003,
    "gG-RPM 0040" to SKU_0604,
    "gG BGM 0005" to SKU_0005,
    "0062" to SKU_0062,
    "gG BPM 0603" to SKU_0603,
    "gG BS 0222" to SKU_0222,
    "gG BS 0220" to SKU_0220,
    "BS1711-B" to SKU_0220,
    "Smart Blood Pressure Monitor" to SKU_0663,
    "gG BPM 0667" to SKU_0661,
    "gG BPM 0634" to SKU_0634,
    "gG BS 0351" to DEFAULT_SKU,
    "gG BS 0344" to SKU_0344,
  )
}
