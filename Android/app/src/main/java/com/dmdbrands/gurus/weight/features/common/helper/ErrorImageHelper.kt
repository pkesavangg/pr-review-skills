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
   * @param selected Whether to return the filled/selected version of the error image
   * @return The drawable resource ID for the error image, or null if not found
   */
  fun getErrorImageDrawable(errorCode: String, selected: Boolean = false): Int? {
    return when (errorCode) {
      "t163" -> if (selected) R.drawable.error_t163_filled else R.drawable.error_t163
      "t164" -> if (selected) R.drawable.error_t164_filled else R.drawable.error_t164
      "t165" -> if (selected) R.drawable.error_t165_filled else R.drawable.error_t165
      "t204" -> if (selected) R.drawable.error_t204_filled else R.drawable.error_t204
      "t205" -> if (selected) R.drawable.error_t205_filled else R.drawable.error_t205
      "t206" -> if (selected) R.drawable.error_t206_filled else R.drawable.error_t206
      "t315" -> if (selected) R.drawable.error_t315_filled else R.drawable.error_t315
      "t323" -> if (selected) R.drawable.error_t323_filled else R.drawable.error_t323
      "t325" -> if (selected) R.drawable.error_t325_filled else R.drawable.error_t325
      else -> null
    }
  }

  fun getErrorImageDrawableRectangle(errorCode: String, selected: Boolean = false): Int {
    return when (errorCode) {
      "t163" -> if (selected) R.drawable.t163_filled_0384 else R.drawable.t163_outlined_0384
      "t164" -> if (selected) R.drawable.t164_filled_0384 else R.drawable.t164_outlined_0384
      "t165" -> if (selected) R.drawable.t165_filled_0384 else R.drawable.t165_outlined_0384
      "t204" -> if (selected) R.drawable.t204_filled_0384 else R.drawable.t204_outlined_0384
      "t205" -> if (selected) R.drawable.t205_filled_0384 else R.drawable.t205_outlined_0384
      "t206" -> if (selected) R.drawable.t206_filled_0384 else R.drawable.t206_outlined_0384
      "t315" -> if (selected) R.drawable.t315_filled_0384 else R.drawable.t315_outlined_0384
      "t323" -> if (selected) R.drawable.t323_filled_0384 else R.drawable.t323_outlined_0384
      "t325" -> if (selected) R.drawable.t325_filled_0384 else R.drawable.t325_outlined_0384
      else -> R.drawable.t325_outlined_0384
    }
  }

  /**
   * Checks if the given error code is supported.
   *
   * @param errorCode The error code to check
   * @return true if the error code is supported, false otherwise
   */
  fun isSupportedErrorCode(errorCode: String): Boolean {
    return when (errorCode) {
      "t163", "t164", "t165", "t204", "t205", "t206", "t315", "t323", "t325" -> true
      else -> false
    }
  }
}
