package com.dmdbrands.gurus.weight.core.navigation

import androidx.compose.runtime.compositionLocalOf
import com.dmdbrands.gurus.weight.domain.services.IProductSelectionManager

/**
 * CompositionLocal for providing the product selection manager throughout the Compose hierarchy.
 * Provided at app level in MeApp, alongside LocalNavBackStack.
 */
val LocalProductSelectionManager =
  compositionLocalOf<IProductSelectionManager> {
    error("No ProductSelectionManager provided")
  }
