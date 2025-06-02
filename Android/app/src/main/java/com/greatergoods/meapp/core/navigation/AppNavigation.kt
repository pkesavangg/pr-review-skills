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
import com.greatergoods.meapp.features.common.viewmodel.AppViewModel
import com.greatergoods.meapp.features.sample.DeviceOverviewScreen
import com.greatergoods.meapp.features.sample.DeviceSettingsScreen
import com.greatergoods.meapp.features.sample.FeedsScreen
import com.greatergoods.meapp.features.sample.HomeScreen
import com.greatergoods.meapp.features.sample.MyScalesScreen
import com.greatergoods.meapp.features.sample.ProductDetailScreen
import com.greatergoods.meapp.features.sample.ProductListScreen
import com.greatergoods.meapp.features.sample.SampleThemeScreen
import com.greatergoods.meapp.theme.MeAppTheme

/**
 * Main navigation composable for the app, handling top-level navigation and back stack management.
 *
 * @param appViewModel The ViewModel managing navigation intents and state.
 */
@Composable
fun AppNavigation() {
    val appViewModel: AppViewModel = hiltViewModel()
    val uiState by appViewModel.uiState.collectAsState()
    val topLevelBackStack = rememberTopLevelBackStack(AppRoute.Init.SampleScreen)
    NavigationObserver(
        appViewModel.appEventService.navigationIntent,
        topLevelBackStack,
    )
    val selectedRoute = topLevelBackStack.topLevelKey as AppRoute
    MeAppTheme(
        darkTheme =
            when (uiState.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.UNRECOGNIZED -> isSystemInDarkTheme()
                ThemeMode.UNSET -> isSystemInDarkTheme()
            },
    ) {
        CompositionLocalProvider(LocalNavBackStack provides topLevelBackStack) {
            HomeScreen(
                selectedRoute = selectedRoute,
            ) {
                NavDisplay(
                    backStack = topLevelBackStack.backStack,
                    onBack = { topLevelBackStack.removeLast() },
                    entryProvider =
                        entryProvider {
                            initEntries(appViewModel)
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
 * @param appViewModel The AppViewModel for app-wide state and updates.
 */
fun EntryProviderBuilder<NavKey>.initEntries(appViewModel: AppViewModel) {
    entry<AppRoute.Init.SampleScreen> {
        val uiState by appViewModel.uiState.collectAsState()
        SampleThemeScreen(
            selectedMode = uiState.themeMode,
            onModeSelected = { appViewModel.setThemeMode(it) },
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
