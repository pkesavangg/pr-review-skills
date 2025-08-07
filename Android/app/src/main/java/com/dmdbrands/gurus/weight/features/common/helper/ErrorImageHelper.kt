package com.dmdbrands.gurus.weight.features.common.helper

import com.dmdbrands.gurus.weight.R

/**
 * Helper object for retrieving error image drawables based on error codes.
 */
object ErrorImageHelper {
    
    /**
     * Gets the error image drawable resource ID for the given error code.
     * 
     * @param errorCode The error code (e.g., 't163', 't204', etc.)
     * @return The drawable resource ID for the error image, or null if not found
     */
    fun getErrorImageDrawable(errorCode: String): Int? {
        return when (errorCode) {
            "t163" -> R.drawable.error_t163
            "t164" -> R.drawable.error_t164
            "t165" -> R.drawable.error_t165
            "t204" -> R.drawable.error_t204
            "t205" -> R.drawable.error_t205
            "t206" -> R.drawable.error_t206
            "t315" -> R.drawable.error_t315
            "t323" -> R.drawable.error_t323
            "t325" -> R.drawable.error_t325
            else -> null
        }
    }
    
    /**
     * Checks if the given error code is supported.
     * 
     * @param errorCode The error code to check
     * @return true if the error code is supported, false otherwise
     */
    fun isSupportedErrorCode(errorCode: String): Boolean {
        return getErrorImageDrawable(errorCode) != null
    }
} 