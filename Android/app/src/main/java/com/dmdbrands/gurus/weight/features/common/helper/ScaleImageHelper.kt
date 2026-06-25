package com.dmdbrands.gurus.weight.features.common.helper

import android.content.Context
import com.dmdbrands.gurus.weight.R
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper
import com.dmdbrands.gurus.weight.resources.AppIcons

object ScaleUtility {

  private val IMAGE_MAP: Map<String, Int> = mapOf(
    // Scales
    "0220" to R.drawable.scale_0220,
    "0222" to R.drawable.scale_0222,
    "0340" to R.drawable.scale_0341, // 0340 (AppSync) uses the 0341 image
    "0341" to R.drawable.scale_0341,
    "0342" to R.drawable.scale_0342,
    "0343" to R.drawable.scale_0343,
    "0345" to R.drawable.scale_0345,
    "0346" to R.drawable.scale_0346,
    "0347" to R.drawable.scale_0347,
    "0358" to R.drawable.scale_0358,
    "0359" to R.drawable.scale_0359,
    "0364" to R.drawable.scale_0364,
    "0369" to R.drawable.scale_0369,
    "0370" to R.drawable.scale_0370,
    "0371" to R.drawable.scale_0371,
    "0375" to R.drawable.scale_0375,
    "0376" to R.drawable.scale_0376,
    "0378" to R.drawable.scale_0378,
    "0380" to R.drawable.scale_0380,
    "0382" to R.drawable.scale_0382,
    "0383" to R.drawable.scale_0383,
    "0384" to R.drawable.scale_0384,
    "0385" to R.drawable.scale_0385,
    "0396" to R.drawable.scale_0396,
    "0397" to R.drawable.scale_0396, // 0397 uses the 0396 image
    "0412" to R.drawable.scale_0412,
    // BPM Monitors
    "0603" to R.drawable.monitor_0603,
    "0604" to R.drawable.monitor_0604,
    "0634" to R.drawable.monitor_0634,
    "0636" to R.drawable.monitor_0636,
    "0661" to R.drawable.monitor_0661,
    "0663" to R.drawable.monitor_0663,
  )

  private val PLACEHOLDER = R.drawable.me_placeholder

  /**
   * Returns the drawable resource for a given scale/monitor SKU.
   * Uses a compile-time map so Compose previews work correctly.
   */
  fun scaleImageResource(sku: String?): Int {
    if (sku.isNullOrEmpty()) return PLACEHOLDER
    val lookupSku = DeviceHelper.primaryBpmSku(sku)
    return IMAGE_MAP[lookupSku] ?: PLACEHOLDER
  }
}
