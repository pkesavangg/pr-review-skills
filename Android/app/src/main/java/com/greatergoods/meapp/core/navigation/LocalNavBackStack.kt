package com.greatergoods.meapp.core.navigation

import androidx.compose.runtime.compositionLocalOf
import androidx.navigation3.runtime.NavKey

val LocalNavBackStack = compositionLocalOf<TopLevelBackStack<NavKey>> {
    error("No navigation back stack provided")
}
