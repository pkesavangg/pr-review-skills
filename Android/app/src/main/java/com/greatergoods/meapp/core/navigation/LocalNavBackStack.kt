package com.greatergoods.meapp.core.navigation

import androidx.compose.runtime.compositionLocalOf
import androidx.navigation3.runtime.NavBackStack

val LocalNavBackStack = compositionLocalOf<NavBackStack> {
    error("No navigation back stack provided")
}
