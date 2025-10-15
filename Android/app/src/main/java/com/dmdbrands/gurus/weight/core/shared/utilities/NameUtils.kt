package com.dmdbrands.gurus.weight.core.shared.utilities

/**
 * Utility functions for handling name-related operations.
 *
 * This class provides utilities for managing user names with the 20-character limit
 * imposed by the Greater Goods Bluetooth SDK to prevent duplicate user errors.
 */
object NameUtils {

  /**
   * Maximum character limit for user names as enforced by the GG Bluetooth SDK.
   * Names longer than this will be truncated to prevent duplicate user errors.
   */
  const val MAX_NAME_LENGTH = 20

  /**
   * Trims a name to the maximum allowed length for the GG Bluetooth SDK.
   *
   * @param name The name to trim
   * @return The trimmed name, or "Default" if the input is null or empty
   */
  fun trimNameForSDK(name: String?): String {
    return when {
      name.isNullOrEmpty() -> "Default"
      name.length <= MAX_NAME_LENGTH -> name
      else -> name.take(MAX_NAME_LENGTH)
    }
  }

  /**
   * Checks if a name exceeds the maximum allowed length.
   *
   * @param name The name to check
   * @return true if the name exceeds the maximum length, false otherwise
   */
  fun isNameTooLong(name: String?): Boolean {
    return !name.isNullOrEmpty() && name.length > MAX_NAME_LENGTH
  }

  /**
   * Gets a warning message for names that exceed the maximum length.
   *
   * @param name The name that exceeds the limit
   * @return A warning message indicating the name will be truncated
   */
  fun getTruncationWarning(name: String): String {
    return "Name will be truncated to 20 characters: \"${name.take(MAX_NAME_LENGTH)}\""
  }
}
