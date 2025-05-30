package com.greatergoods.meapp.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.EntryProviderBuilder
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.greatergoods.meapp.features.common.viewmodel.NavigationViewmodel
import com.greatergoods.meapp.features.sample.DeviceOverviewScreen
import com.greatergoods.meapp.features.sample.DeviceSettingsScreen
import com.greatergoods.meapp.features.sample.FeedsScreen
import com.greatergoods.meapp.features.sample.HomeScreen
import com.greatergoods.meapp.features.sample.MyScalesScreen
import com.greatergoods.meapp.features.sample.ProductDetailScreen
import com.greatergoods.meapp.features.sample.ProductListScreen
import com.greatergoods.meapp.features.sample.SampleThemeScreen

/**
 * Main navigation composable for the app, handling top-level navigation and back stack management.
 *
 * @param navigationViewModel The ViewModel managing navigation intents and state.
 */
@Composable
fun AppNavigation(
    navigationViewModel: NavigationViewmodel,
) {
    val topLevelBackStack = rememberTopLevelBackStack(AppRoute.Init.SampleScreen)
    val isDark = remember { mutableStateOf(false) }
    NavigationObserver(
        navigationViewModel.appEventService.navigationIntent,
        topLevelBackStack,
    )
    val selectedRoute = topLevelBackStack.topLevelKey as AppRoute
    CompositionLocalProvider(LocalNavBackStack provides topLevelBackStack) {
        HomeScreen(
            selectedRoute = selectedRoute,
        ) {
            NavDisplay(
                backStack = topLevelBackStack.backStack,
                onBack = { topLevelBackStack.removeLast() },
                entryProvider = entryProvider {
                    initEntries(isDark.value) { isDark.value = !isDark.value }
                    mainEntries()
                    productEntries()
                },
            )
        }
    }
}

/**
 * Registers the entry for the initial theme sample screen.
 *
 * @param isDark Whether the theme is dark mode.
 * @param toggleTheme Callback to toggle the theme.
 */
fun EntryProviderBuilder<NavKey>.initEntries(
    isDark: Boolean,
    toggleTheme: () -> Unit
) {
    entry<AppRoute.Init.SampleScreen> {
        SampleThemeScreen(isDark = isDark, onToggleTheme = toggleTheme)
    }
}

/**
 * Registers the entries for the main feature screens.
 *
 * @param onNavigate Callback to navigate to a given AppRoute.
 */
fun EntryProviderBuilder<NavKey>.mainEntries() {
    entry<AppRoute.Main.Feeds> { FeedsScreen() }
    entry<AppRoute.Main.MyScales> { MyScalesScreen() }
    entry<AppRoute.Main.DeviceDetail.Overview> { DeviceOverviewScreen() }
    entry<AppRoute.Main.DeviceDetail.Settings> { DeviceSettingsScreen() }
}

/**
 * Registers the entries for the product feature screens.
 *
 * @param onProductClick Callback when a product is selected.
 */
fun EntryProviderBuilder<NavKey>.productEntries() {
    entry<AppRoute.Product.ProductList> { ProductListScreen() }
    entry<AppRoute.Product.ProductDetail> { key -> ProductDetailScreen(key.id) }
}
