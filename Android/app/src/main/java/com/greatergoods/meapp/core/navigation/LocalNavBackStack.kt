package com.greatergoods.meapp.core.navigation

import androidx.compose.runtime.compositionLocalOf
import androidx.navigation3.runtime.NavKey

/**
 * CompositionLocal for providing the top-level navigation back stack throughout the Compose hierarchy.
 * Throws an error if not provided.
 */
val LocalNavBackStack =
    compositionLocalOf<TopLevelBackStack<NavKey>> {
        error("No navigation back stack provided")
    }
