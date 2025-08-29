package com.dmdbrands.gurus.weight.features.common.helper

import com.dmdbrands.gurus.weight.R
import com.dmdbrands.gurus.weight.resources.AppIcons

object ScaleUtility {
  /**
   * Returns the drawable resource for a given scale SKU, or placeholder image if not found.
   * Uses reflection to dynamically access the drawable resource based on SKU.
   */
  fun scaleImageResource(sku: String?): Int {
    // Special case: SKU 0378 uses the image for 0383
    // val sku = if (sku == "0341" || sku == "0380") "0412" else sku
    return try {
      // Use reflection to get the field from R.drawable class
      val fieldName = "s_$sku"
      val field = R.drawable::class.java.getDeclaredField(fieldName)
      field.getInt(null) // Static field, so pass null
    } catch (e: Exception) {
      // Return placeholder if the resource doesn't exist
      AppIcons.Default.ScalePlaceholder
    }
  }
}
