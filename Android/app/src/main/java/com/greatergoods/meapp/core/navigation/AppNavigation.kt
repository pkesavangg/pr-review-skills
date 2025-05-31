package com.greatergoods.meapp.core.navigation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderBuilder
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.greatergoods.meapp.data.storage.datastore.ThemeMode
import com.greatergoods.meapp.features.sample.DeviceOverviewScreen
import com.greatergoods.meapp.features.sample.DeviceSettingsScreen
import com.greatergoods.meapp.features.sample.FeedsScreen
import com.greatergoods.meapp.features.sample.HomeScreen
import com.greatergoods.meapp.features.sample.MyScalesScreen
import com.greatergoods.meapp.features.sample.ProductDetailScreen
import com.greatergoods.meapp.features.sample.ProductListScreen
import com.greatergoods.meapp.features.sample.SampleThemeScreen
import com.greatergoods.meapp.features.theme.ThemeViewModel
import com.greatergoods.meapp.theme.MeAppTheme

/**
 * Main navigation composable for the app, handling top-level navigation and back stack management.
 *
 * @param navigationViewModel The ViewModel managing navigation intents and state.
 */
@Composable
fun AppNavigation(
) {
    val themeViewModel: ThemeViewModel = hiltViewModel()
    val themeMode by themeViewModel.themeMode.collectAsState()
    val topLevelBackStack = rememberTopLevelBackStack(AppRoute.Init.SampleScreen)
    NavigationObserver(
        themeViewModel.appEventService.navigationIntent,
        topLevelBackStack,
    )
    val selectedRoute = topLevelBackStack.topLevelKey as AppRoute
    MeAppTheme(
        darkTheme = when (themeMode) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.UNRECOGNIZED -> isSystemInDarkTheme()
        },
    ) {
        CompositionLocalProvider(LocalNavBackStack provides topLevelBackStack) {
            HomeScreen(
                selectedRoute = selectedRoute,
            ) {
                NavDisplay(
                    backStack = topLevelBackStack.backStack,
                    onBack = { topLevelBackStack.removeLast() },
                    entryProvider = entryProvider {
                        initEntries(themeViewModel)
                        mainEntries()
                        productEntries()
                    },
                )
            }
        }
    }
}

/**
 * Registers the entry for the initial theme sample screen.
 *
 * @param themeViewModel The ThemeViewModel for theme state and updates.
 */
fun EntryProviderBuilder<NavKey>.initEntries(
    themeViewModel: ThemeViewModel
) {
    entry<AppRoute.Init.SampleScreen> {
        val themeMode by themeViewModel.themeMode.collectAsState()
        SampleThemeScreen(
            selectedMode = themeMode,
            onModeSelected = { themeViewModel.setThemeMode(it) },
        )
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
