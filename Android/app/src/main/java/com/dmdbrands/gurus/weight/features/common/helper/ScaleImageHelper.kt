package com.dmdbrands.gurus.weight.features.common.helper

import com.dmdbrands.gurus.weight.R
import com.dmdbrands.gurus.weight.resources.AppIcons

object ScaleUtility {
  /**
   * Returns the drawable resource for a given scale SKU, or placeholder image if not found.
   * Uses reflection to dynamically access the drawable resource based on SKU.
   */
  fun scaleImageResource(sku: String?): Int {
    // SKU aliases: paired devices that ship the same hardware image.
    //   - 0340 reuses the image for 0341 (AppSync Body Fat Scale)
    //   - 0397 reuses the image for 0396 (Wi-Fi scale)
    val resolvedSku = when (sku) {
      "0340" -> "0341"
      "0397" -> "0396"
      else -> sku
    }
    return try {
      // Use reflection to get the field from R.drawable class
      val fieldName = "scale_$resolvedSku"
      val field = R.drawable::class.java.getDeclaredField(fieldName)
      field.getInt(null) // Static field, so pass null
    } catch (e: Exception) {
      // Return placeholder if the resource doesn't exist
      AppIcons.Default.ScalePlaceholder
    }
  }
}
