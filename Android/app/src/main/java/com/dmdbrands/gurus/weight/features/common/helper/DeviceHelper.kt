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
  const val SKU_0636 = "0636"

  /** All known Baby Scale SKUs. */
  val BABY_SCALE_SKUS: Set<String> = setOf(SKU_0220, SKU_0222)

  /** Returns `true` when the given [sku] identifies a Baby Scale. */
  fun isBabyScale(sku: String): Boolean = sku in BABY_SCALE_SKUS

  /** All known Blood Pressure Monitor SKUs. */
  val BPM_SKUS: Set<String> = setOf(SKU_0603, SKU_0661, SKU_0634, SKU_0663)

  /** Returns `true` when the given [sku] identifies a Blood Pressure Monitor. */
  fun isBpmDevice(sku: String?): Boolean = sku in BPM_SKUS

  fun GGDeviceDetail.getSKU() = SKU_MAP[deviceName] ?: DEFAULT_SKU

  /**
   * Maps a SKU to its display SKU for UI purposes only.
   * For example, SKU 0022 is displayed as 0383.
   * This should only be used for display, not for saving.
   * @param sku The SKU to map for display
   * @return The display SKU
   */
  fun mapSkuForDisplay(sku: String): String = if (sku == SKU_0022) SKU_0383 else sku

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
    "gG BPM 0663" to SKU_0663,
    "gG BPM 0667" to SKU_0661,
    "gG BPM 0634" to SKU_0634,
    "gG BS 0351" to DEFAULT_SKU,
    "gG BS 0344" to SKU_0344,
    "gG BPM 0636" to SKU_0636,
  )
}
