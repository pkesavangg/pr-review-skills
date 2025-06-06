package com.greatergoods.meapp

import androidx.navigation3.runtime.EntryProviderBuilder
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import com.greatergoods.meapp.core.logging.LogManager
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.features.common.viewmodel.AppViewModel
import com.greatergoods.meapp.features.sample.DeviceSettingsScreen
import com.greatergoods.meapp.features.sample.FeedsScreen
import com.greatergoods.meapp.features.sample.MyScalesScreen
import com.greatergoods.meapp.features.sample.ProductDetailScreen
import com.greatergoods.meapp.features.sample.ProductListScreen
import com.greatergoods.meapp.presentation.screens.AddEntryScreen
import com.greatergoods.meapp.presentation.screens.EntryScreen

/**
 * Registers the entry for the initial theme sample screen.
 *
 * @param appViewModel The AppViewModel for app state and updates.
 * @param logManager The LogManager for logging events.
 */
fun EntryProviderBuilder<NavKey>.initEntries(
    appViewModel: AppViewModel,
    logManager: LogManager
) {
    entry<AppRoute.Init.SampleScreen> {
        EntryScreen(
            onNavigateToAddEntry = {
                // Navigate to add entry screen
            },
        )
    }

    entry<AppRoute.Main.AddEntry> {
        AddEntryScreen()
    }
}

/**
 * Registers the entries for the main feature screens.
 *
 */
fun EntryProviderBuilder<NavKey>.mainEntries() {
    entry<AppRoute.Main.Feeds> { FeedsScreen() }
    entry<AppRoute.Main.MyScales> { MyScalesScreen() }
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
