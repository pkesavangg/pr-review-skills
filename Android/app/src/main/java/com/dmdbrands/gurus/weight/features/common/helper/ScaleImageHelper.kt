package com.dmdbrands.gurus.weight.features.common.helper

import android.content.Context
import com.dmdbrands.gurus.weight.resources.AppIcons

object ScaleUtility {
  /**
   * Returns the drawable resource for a given scale SKU, or placeholder image if not found.
   * Uses Resources.getIdentifier() which is R8/ProGuard-safe.
   */
  fun scaleImageResource(context: Context, sku: String?): Int {
    // Special case: SKU 0397 uses the image for 0396
    val resolvedSku = if (sku == "0397") "0396" else sku
    if (resolvedSku.isNullOrEmpty()) return AppIcons.Default.ScalePlaceholder
    val prefix = if (DeviceHelper.isBpmDevice(resolvedSku)) "monitor" else "scale"
    val resId = context.resources.getIdentifier(
      "${prefix}_$resolvedSku",
      "drawable",
      context.packageName,
    )
    return if (resId != 0) resId else AppIcons.Default.ScalePlaceholder
  }
}
