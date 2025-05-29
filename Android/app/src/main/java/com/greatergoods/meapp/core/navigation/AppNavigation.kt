package com.greatergoods.meapp.core.navigation

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.greatergoods.meapp.domain.interfaces.NavigationIntent
import kotlinx.coroutines.flow.Flow

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@Composable
fun AppNavigation(
    backStack: NavBackStack,
    navigationIntent: Flow<NavigationIntent>,
    onBack: () -> Unit
) {
    NavigationObserver(navigationIntent, backStack, baseClass = AppRoute.Init::class)
    CompositionLocalProvider(LocalNavBackStack provides backStack) {
        NavDisplay(
            backStack = backStack,
            onBack = { onBack() },
            entryProvider = entryProvider {
                // Top-level routes
            }
        )
    }
}
