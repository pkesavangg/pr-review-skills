package com.dmdbrands.gurus.weight.features.common.helper

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object StringUtil {
  fun String.displayName(): String {
    return this.replace("_", " ")
  }

  /**
   * Formats a Unix timestamp to a human-readable date string.
   */
  fun Long.formatTimestamp(): String {
    val date = Date(this * 1000) // Convert from seconds to milliseconds
    val formatter = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
    return formatter.format(date)
  }

  fun String.cleanCorruptedChars(): String {
    return this.replace(Regex("[^\\p{Print}]"), "")
  }
}
