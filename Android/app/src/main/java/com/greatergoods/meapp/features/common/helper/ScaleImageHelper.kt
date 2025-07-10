package com.greatergoods.meapp.features.common.helper

import com.greatergoods.meapp.R
import com.greatergoods.meapp.resources.AppIcons

object ScaleUtility {
    /**
     * Returns the drawable resource for a given scale SKU, or placeholder image if not found.
     * Uses reflection to dynamically access the drawable resource based on SKU.
     */
    fun scaleImageResource(sku: String?): Int {
        // Special case: SKU 0378 uses the image for 0383
        val targetSku = if (sku == "0378") "0383" else sku

        return try {
            // Use reflection to get the field from R.drawable class
            val fieldName = "_$targetSku"
            val field = R.drawable::class.java.getDeclaredField(fieldName)
            field.getInt(null) // Static field, so pass null
        } catch (e: Exception) {
            // Return placeholder if the resource doesn't exist
            AppIcons.Default.ScalePlaceholder
        }
    }
}
