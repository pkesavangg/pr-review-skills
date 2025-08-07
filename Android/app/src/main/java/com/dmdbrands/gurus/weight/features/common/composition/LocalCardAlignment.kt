package com.dmdbrands.gurus.weight.features.common.composition

import androidx.compose.runtime.staticCompositionLocalOf
import com.dmdbrands.gurus.weight.features.common.components.CardAlignmentType

/**
 * CompositionLocal to provide a default card alignment type.
 * This allows customizing card alignment from a higher-level composable
 * without passing it down explicitly through parameters.
 *
 * Defaults to [CardAlignmentType.TopStart].
 */
val LocalCardAlignment = staticCompositionLocalOf { CardAlignmentType.TopStart }
