package com.dmdbrands.gurus.weight.features.common.helper

import android.content.Context
import com.dmdbrands.gurus.weight.resources.AppIcons

object ScaleUtility {
  /**
   * Returns the drawable resource for a given scale SKU, or placeholder image if not found.
   *
   * Lookup uses Resources.getIdentifier(), which survives R8 code shrinking but is invisible to
   * R8's resource shrinker. The scale_* and monitor_* drawables this resolves are kept in release
   * builds by res/raw/keep.xml — keep that file in sync if this naming convention ever changes.
   */
  fun scaleImageResource(context: Context, sku: String?): Int {
    // Special case: SKU 0397 uses the image for 0396
    val resolvedSku = if (sku == "0397") "0396" else sku ?: return AppIcons.Default.ScalePlaceholder
    if (resolvedSku.isEmpty()) return AppIcons.Default.ScalePlaceholder
    val prefix = if (DeviceHelper.isBpmDevice(resolvedSku)) "monitor" else "scale"
    val resId = context.resources.getIdentifier(
      "${prefix}_$resolvedSku",
      "drawable",
      context.packageName,
    )
    return if (resId != 0) resId else AppIcons.Default.ScalePlaceholder
  }
}
