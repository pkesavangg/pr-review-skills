package com.greatergoods.meapp

import androidx.navigation3.runtime.EntryProviderBuilder
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.features.auth.UserListScreen
import com.greatergoods.meapp.features.login.LoginScreen
import com.greatergoods.meapp.features.sample.HomeScreen
import com.greatergoods.meapp.features.common.viewmodel.AppViewModel
import com.greatergoods.meapp.features.sample.DeviceSettingsScreen
import com.greatergoods.meapp.features.sample.FeedsScreen
import com.greatergoods.meapp.features.sample.MyScalesScreen
import com.greatergoods.meapp.features.sample.ProductDetailScreen
import com.greatergoods.meapp.features.sample.ProductListScreen
import com.greatergoods.meapp.features.sample.SampleThemeScreen
import com.greatergoods.meapp.core.logging.LogManager
import javax.inject.Inject

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
        SampleThemeScreen(appViewModel = appViewModel)
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
fun EntryProviderBuilder<NavKey>.homeEntries() {
    entry<AppRoute.Home.HomeScreen> { HomeScreen() }
}

/**
 * Registers the entries for the authentication screens.
 *
 */
fun EntryProviderBuilder<NavKey>.authEntries() {
    entry<AppRoute.Auth.LoginScreen> { LoginScreen() }
    entry<AppRoute.Auth.UserListScreen> { UserListScreen() }
}
