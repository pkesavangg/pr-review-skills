package com.dmdbrands.gurus.weight.core.navigation

import androidx.compose.runtime.compositionLocalOf
import com.dmdbrands.gurus.weight.domain.enums.ProductType

/**
 * CompositionLocal for providing the currently selected [ProductType] throughout the Compose hierarchy.
 * Defaults to [ProductType.MY_WEIGHT] if not explicitly provided.
 */
val LocalProductType =
    compositionLocalOf { ProductType.MY_WEIGHT }
