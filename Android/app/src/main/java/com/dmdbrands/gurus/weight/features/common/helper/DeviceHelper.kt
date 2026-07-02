package com.dmdbrands.gurus.weight.features.common.helper

import com.dmdbrands.gurus.weight.domain.enums.ProductType
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
  const val SKU_0664 = "0664"
  const val SKU_0639 = "0639"
  const val SKU_0665 = "0665"
  const val SKU_0667 = "0667"

  /** All known Baby Scale SKUs. */
  val BABY_SCALE_SKUS: Set<String> = setOf(SKU_0220, SKU_0222)

  /** Returns `true` when the given [sku] identifies a Baby Scale. */
  fun isBabyScale(sku: String): Boolean = sku in BABY_SCALE_SKUS

  /**
   * Alternate baby-scale model numbers → primary SKU used for pairing, storage, and asset lookup.
   * Mirrors [BPM_ALTERNATE_TO_PRIMARY_SKU]: 0220/0222 are the same product, so 0222 collapses to 0220.
   */
  val BABY_ALTERNATE_TO_PRIMARY_SKU: Map<String, String> = mapOf(
    SKU_0222 to SKU_0220,
  )

  /**
   * Alternate stamped model numbers → primary SKU used for pairing, storage, and asset lookup.
   * Mirrors iOS `bpmAlternateToPrimarySku` in `Bpms.swift`.
   */
  val BPM_ALTERNATE_TO_PRIMARY_SKU: Map<String, String> = mapOf(
    SKU_0664 to SKU_0604,
    SKU_0639 to SKU_0636,
    SKU_0665 to SKU_0663,
    SKU_0667 to SKU_0661,
  )

  /** All known Blood Pressure Monitor SKUs (primaries + alternates). */
  val BPM_SKUS: Set<String> =
    setOf(SKU_0603, SKU_0604, SKU_0634, SKU_0636, SKU_0661, SKU_0663) +
      BPM_ALTERNATE_TO_PRIMARY_SKU.keys

  /** Returns `true` when [sku] is non-null and identifies a Blood Pressure Monitor; `false` when [sku] is null. */
  fun isBpmDevice(sku: String?): Boolean = sku != null && sku in BPM_SKUS

  /**
   * Single source of truth mapping a device [sku] to the account [ProductType] it belongs to.
   * Baby scales → [ProductType.BABY], BP monitors → [ProductType.BLOOD_PRESSURE], everything else
   * → [ProductType.MY_WEIGHT]. Used when saving a device so the account's productTypes reflects the
   * paired hardware. (MOB-596)
   */
  fun productTypeForSku(sku: String): ProductType = when {
    isBabyScale(sku) -> ProductType.BABY
    isBpmDevice(sku) -> ProductType.BLOOD_PRESSURE
    else -> ProductType.MY_WEIGHT
  }

  /**
   * Resolves a variant BPM SKU to its primary (canonical) SKU. Non-variant SKUs pass through unchanged.
   * Use at persistence boundaries so the database and server never see alternate codes.
   */
  fun primaryBpmSku(sku: String): String = BPM_ALTERNATE_TO_PRIMARY_SKU[sku] ?: sku

  /**
   * Resolves any alternate SKU (BPM or baby scale) to its primary (canonical) SKU. Non-variant SKUs
   * pass through unchanged. Used to collapse paired models (e.g. 0222→0220, 0664→0604) into one entry.
   */
  fun primarySku(sku: String): String =
    BPM_ALTERNATE_TO_PRIMARY_SKU[sku] ?: BABY_ALTERNATE_TO_PRIMARY_SKU[sku] ?: sku

  /**
   * Label shown next to the product image in the monitor list (grouped model numbers, e.g. "0604/0664").
   * Mirrors iOS `bpmListModelLabel` in `Bpms.swift`.
   */
  fun bpmListModelLabel(primarySku: String): String = when (primarySku) {
    SKU_0604 -> "$SKU_0604/$SKU_0664"
    SKU_0636 -> "$SKU_0636/$SKU_0639"
    SKU_0663 -> "$SKU_0663/$SKU_0665"
    SKU_0661 -> "$SKU_0661/$SKU_0667"
    else -> primarySku
  }

  /** Grouped model-number label for the baby-scale pair, e.g. "0220/0222". Non-pair SKUs pass through. */
  fun babyScaleListModelLabel(primarySku: String): String =
    if (primarySku == SKU_0220) "$SKU_0220/$SKU_0222" else primarySku

  /**
   * Grouped model-number label for the device list and setup header. Resolves [sku] to its primary,
   * then returns the paired label ("0604/0664", "0220/0222", …). Non-paired SKUs fall back to
   * [mapSkuForDisplay] (handles the legacy 0022→0383 case), otherwise the SKU itself.
   */
  fun listModelLabel(sku: String): String {
    val primary = primarySku(sku)
    return when {
      isBpmDevice(primary) -> bpmListModelLabel(primary)
      isBabyScale(primary) -> babyScaleListModelLabel(primary)
      else -> mapSkuForDisplay(sku)
    }
  }

  fun GGDeviceDetail.getSKU(): String? = SKU_MAP[deviceName]

  /**
   * Maps a SKU to its display SKU for UI purposes only.
   * Resolves BPM variant SKUs to their primary (e.g. 0664 → 0604) and the legacy 0022 → 0383 case.
   * This should only be used for display or catalog lookup, not as the source of truth for persistence.
   * @param sku The SKU to map for display
   * @return The display SKU
   */
  fun mapSkuForDisplay(sku: String): String =
    BPM_ALTERNATE_TO_PRIMARY_SKU[sku] ?: if (sku == SKU_0022) SKU_0383 else sku

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
    "gG BPM 0665" to SKU_0665,
    "gG BPM 0667" to SKU_0667,
    "gG BPM 0634" to SKU_0634,
    "gG BS 0351" to DEFAULT_SKU,
    "gG BS 0344" to SKU_0344,
    "gG BPM 0636" to SKU_0636,
    "gG BPM 0639" to SKU_0639,
    "gG BPM 0664" to SKU_0664,
  )
}
