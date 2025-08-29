package com.greatergoods.ggInAppMessaging.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import android.util.Log

/**
 * Configuration class for IAM package that provides theme-aware colors
 * Uses Compose state management for proper recomposition
 */
object IAMConfiguration {
  private val _colorTokensState = mutableStateOf<ColorTokens?>(null)

  /**
   * Observable state of ColorTokens that screens can observe
   */
  val colorTokensState: State<ColorTokens?> = _colorTokensState

  /**
   * Update IAM configuration with app-specific colors
   * This will trigger recomposition in all observing screens
   */
  fun updateColors(colorTokens: ColorTokens) {
    Log.d("IAMConfiguration", "Updating colors: isDark=${colorTokens.primaryBackground}")
    _colorTokensState.value = colorTokens
  }

  /**
   * Get the current configured ColorTokens
   * @throws IllegalStateException if configuration is not initialized
   */
  fun getColorTokens(): ColorTokens {
    return _colorTokensState.value ?: throw IllegalStateException(
      "IAM Configuration not initialized. Call IAMConfiguration.updateColors() first.",
    )
  }

  /**
   * Check if configuration has been initialized
   */
  fun isInitialized(): Boolean = _colorTokensState.value != null
}

/**
 * Composable function to provide current ColorTokens with automatic recomposition
 * This is the recommended way to use IAM colors in screens
 */
@Composable
fun rememberIAMColors(): ColorTokens? {
  // Use the state value directly to ensure proper recomposition
  return IAMConfiguration.colorTokensState.value
}
