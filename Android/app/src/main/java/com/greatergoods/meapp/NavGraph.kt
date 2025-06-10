package com.greatergoods.meapp

import AddEntryScreen
import androidx.navigation3.runtime.EntryProviderBuilder
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import com.greatergoods.meapp.core.logging.LogManager
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.features.auth.AppViewModel
import com.greatergoods.meapp.features.auth.UserListScreen
import com.greatergoods.meapp.features.dashboard.components.HistoryGraph
import com.greatergoods.meapp.features.sample.HomeScreen

/**
 * Provides the navigation entries for the app.
 *
 * This function is used to register all the navigation entries for the app.
 * It includes both the home and authentication entries.
 *
 * @param entryProviderBuilder The builder to register the entries with.
 */
fun EntryProviderBuilder<NavKey>.initEntries(
    appViewModel: AppViewModel,
    logManager: LogManager
) {
    entry<AppRoute.Main.AddEntry> {
        AddEntryScreen()
    }
}

fun EntryProviderBuilder<NavKey>.homeEntries() {
    entry<AppRoute.Home.HomeScreen> { HomeScreen() }
}

/**
 * Registers the entries for the authentication screens.
 *
 */
fun EntryProviderBuilder<NavKey>.authEntries() {
    entry<AppRoute.Auth.LoginScreen> { HistoryGraph() }
    entry<AppRoute.Auth.UserListScreen> { UserListScreen() }
}
