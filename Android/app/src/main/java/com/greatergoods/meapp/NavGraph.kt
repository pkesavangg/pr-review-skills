package com.greatergoods.meapp

import androidx.navigation3.runtime.EntryProviderBuilder
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.features.auth.UserListScreen
import com.greatergoods.meapp.features.login.LoginScreen
import com.greatergoods.meapp.features.sample.HomeScreen

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
