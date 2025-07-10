package com.greatergoods.meapp.app.components

import androidx.navigation3.runtime.EntryProviderBuilder
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.features.MyAccounts.screen.MyAccountsScreen
import com.greatergoods.meapp.features.addScale.screens.AddScaleScreen
import com.greatergoods.meapp.features.addScale.screens.ChooseScaleScreen
import com.greatergoods.meapp.features.appPermissions.AppPermissionsScreen
import com.greatergoods.meapp.features.appSync.AppSync
import com.greatergoods.meapp.features.changePassword.ChangePasswordScreen
import com.greatergoods.meapp.features.dashboard.DashboardScreen
import com.greatergoods.meapp.features.debugMenu.screen.DebugMenuScreen
import com.greatergoods.meapp.features.goal.screen.GoalScreen
import com.greatergoods.meapp.features.help.screen.HelpScreen
import com.greatergoods.meapp.features.history.HistoryScreen
import com.greatergoods.meapp.features.landing.screen.LandingScreen
import com.greatergoods.meapp.features.landing.screen.MultiAccountLandingScreen
import com.greatergoods.meapp.features.login.screen.LoginScreen
import com.greatergoods.meapp.features.manualEntry.EntryScreen
import com.greatergoods.meapp.features.profile.screen.ProfileScreen
import com.greatergoods.meapp.features.scaleDetails.screens.ScaleDetailsScreen
import com.greatergoods.meapp.features.scaleMode.screens.ScaleModeScreen
import com.greatergoods.meapp.features.settings.SettingsScreen
import com.greatergoods.meapp.features.signup.SignupScreen
import com.greatergoods.meapp.features.weightless.screen.WeightlessScreen

/**
 * Registers the entries for the authentication screens.
 *
 */
fun EntryProviderBuilder<NavKey>.authEntries() {
    entry<AppRoute.Auth.Landing> { LandingScreen() }
    entry<AppRoute.Auth.Login> { credentials ->
        LoginScreen(credentials.email)
    }
    entry<AppRoute.Auth.Signup> { SignupScreen() }
    entry<AppRoute.Auth.MultiAccountLanding> { MultiAccountLandingScreen() }
}

fun EntryProviderBuilder<NavKey>.topLevelEntries() {
    entry<AppRoute.Main.Dashboard> { DashboardScreen() }
    entry<AppRoute.Main.History> { HistoryScreen() }
    entry<AppRoute.Main.Entry> { EntryScreen() } // Placeholder for EntryScreen
    entry<AppRoute.Main.Settings> { SettingsScreen() } // Placeholder for SettingsScreen
    entry<AppRoute.Main.AppSync> { AppSync() } // Placeholder for AppSyncScreen
}

fun EntryProviderBuilder<NavKey>.accountSettingsEntries() {
    entry<AppRoute.AccountSettings.Profile> { ProfileScreen() }
    entry<AppRoute.AccountSettings.ChangePassword> { ChangePasswordScreen() }
    entry<AppRoute.AccountSettings.MyAccounts> { MyAccountsScreen() }
    entry<AppRoute.AccountSettings.Weightless> { WeightlessScreen() }
    entry<AppRoute.AccountSettings.AddEditScales> { AddScaleScreen() }
    entry<AppRoute.AccountSettings.ChooseScale> { ChooseScaleScreen() }
    entry<AppRoute.AccountSettings.Goal> { GoalScreen() }
    entry<AppRoute.AccountSettings.HelpScreen> { HelpScreen() }
    entry<AppRoute.AccountSettings.DebugMenu> { DebugMenuScreen() }
    entry<AppRoute.AccountSettings.ScaleDetails> { scaleInfo ->
        ScaleDetailsScreen(scaleInfo.broadcastId)
    }
    entry<AppRoute.AccountSettings.AppPermissions> { AppPermissionsScreen() }
}

fun EntryProviderBuilder<NavKey>.scaleDetailEntries() {
    entry<AppRoute.ScaleDetails.ScaleMode> { scaleInfo ->
        ScaleModeScreen(scaleInfo.scaleId)
    }
}
