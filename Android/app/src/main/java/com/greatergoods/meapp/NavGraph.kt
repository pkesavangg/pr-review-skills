package com.greatergoods.meapp

import androidx.navigation3.runtime.EntryProviderBuilder
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.features.dashboard.DashboardScreen
import com.greatergoods.meapp.features.login.LoginScreen

/**
 * Registers the entries for the authentication screens.
 *
 */
fun EntryProviderBuilder<NavKey>.authEntries() {
    entry<AppRoute.Auth.LoginScreen> { LoginScreen() }
}

fun EntryProviderBuilder<NavKey>.mainEntries() {
    entry<AppRoute.Dashboard> { DashboardScreen() }
}
