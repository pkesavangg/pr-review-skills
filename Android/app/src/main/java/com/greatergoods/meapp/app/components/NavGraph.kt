package com.greatergoods.meapp.app.components

import androidx.navigation3.runtime.EntryProviderBuilder
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.features.appSync.AppSync
import com.greatergoods.meapp.features.dashboard.DashboardScreen
import com.greatergoods.meapp.features.entry.EntryScreen
import com.greatergoods.meapp.features.history.HistoryScreen
import com.greatergoods.meapp.features.login.LoginScreen
import com.greatergoods.meapp.features.settings.SettingsScreen

/**
 * Registers the entries for the authentication screens.
 *
 */
fun EntryProviderBuilder<NavKey>.authEntries() {
    entry<AppRoute.Auth.Login> { LoginScreen() }
}

fun EntryProviderBuilder<NavKey>.topLevelEntries() {
    entry<AppRoute.Main.Dashboard> { DashboardScreen() }
    entry<AppRoute.Main.History> { HistoryScreen() }
    entry<AppRoute.Main.Entry> { EntryScreen {} } // Placeholder for EntryScreen
    entry<AppRoute.Main.Settings> { SettingsScreen() } // Placeholder for SettingsScreen
    entry<AppRoute.Main.AppSync> { AppSync() } // Placeholder for AppSyncScreen
}
