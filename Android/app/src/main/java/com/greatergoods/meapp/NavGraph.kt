package com.greatergoods.meapp

import androidx.navigation3.runtime.EntryProviderBuilder
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.features.appSync.AppSync
import com.greatergoods.meapp.features.auth.UserListScreen
import com.greatergoods.meapp.features.dashboard.DashBoardScreen
import com.greatergoods.meapp.features.entry.screens.EntryScreen
import com.greatergoods.meapp.features.history.HistoryScreen
import com.greatergoods.meapp.features.login.LoginScreen
import com.greatergoods.meapp.features.settings.SettingsScreen

/**
 * Registers the entries for the authentication screens.
 *
 */
fun EntryProviderBuilder<NavKey>.authEntries() {
    entry<AppRoute.Auth.LoginScreen> { LoginScreen() }
    entry<AppRoute.Auth.UserListScreen> { UserListScreen() }
}

fun EntryProviderBuilder<NavKey>.topLevelEntries() {
    entry<AppRoute.Main.Dashboard> { DashBoardScreen() }
    entry<AppRoute.Main.History> { HistoryScreen() }
    entry<AppRoute.Main.Entry> { EntryScreen {} } // Placeholder for EntryScreen
    entry<AppRoute.Main.Settings> { SettingsScreen() } // Placeholder for SettingsScreen
    entry<AppRoute.Main.AppSync> { AppSync() } // Placeholder for AppSyncScreen
}
