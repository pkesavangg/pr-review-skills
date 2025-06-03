package com.greatergoods.meapp

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation3.runtime.EntryProviderBuilder
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.features.common.viewmodel.AppViewModel
import com.greatergoods.meapp.features.sample.DeviceOverviewScreen
import com.greatergoods.meapp.features.sample.DeviceSettingsScreen
import com.greatergoods.meapp.features.sample.FeedsScreen
import com.greatergoods.meapp.features.sample.MyScalesScreen
import com.greatergoods.meapp.features.sample.ProductDetailScreen
import com.greatergoods.meapp.features.sample.ProductListScreen
import com.greatergoods.meapp.features.sample.SampleThemeScreen
import com.greatergoods.meapp.features.theme.ThemeViewModel
import com.greatergoods.meapp.core.logging.LogManager
import javax.inject.Inject

/**
 * Registers the entry for the initial theme sample screen.
 *
 * @param appViewModel The ThemeViewModel for theme state and updates. 
 * @param themeViewModel The ThemeViewModel for theme state and updates.
 * @param logManager The LogManager for logging events.
 */
fun EntryProviderBuilder<NavKey>.initEntries(
    appViewModel: AppViewModel,
    themeViewModel: ThemeViewModel,
    logManager: LogManager
) {
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
 */
fun EntryProviderBuilder<NavKey>.productEntries() {
    entry<AppRoute.Product.ProductList> { ProductListScreen() }
    entry<AppRoute.Product.ProductDetail> { key -> ProductDetailScreen(key.id) }
}
