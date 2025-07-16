package com.greatergoods.meapp.app.components

import androidx.navigation3.runtime.EntryProviderBuilder
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.features.MyAccounts.screen.MyAccountsScreen
import com.greatergoods.meapp.features.ScaleSetup.screens.AppsyncScaleSetupScreen
import com.greatergoods.meapp.features.ScaleSetup.screens.BtScaleSetupScreen
import com.greatergoods.meapp.features.ScaleSetup.screens.BtWifiScaleSetupScreen
import com.greatergoods.meapp.features.ScaleSetup.screens.LcbtScaleSetupScreen
import com.greatergoods.meapp.features.ScaleSetup.screens.WifiScaleSetupScreen
import com.greatergoods.meapp.features.ScaleUsers.screens.ScaleUserListScreen
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
import com.greatergoods.meapp.features.historyDetail.HistoryDetailScreen
import com.greatergoods.meapp.features.integration.components.HealthConnectIntegrationScreen
import com.greatergoods.meapp.features.integration.screen.IntegrationScreen
import com.greatergoods.meapp.features.landing.screen.LandingScreen
import com.greatergoods.meapp.features.landing.screen.MultiAccountLandingScreen
import com.greatergoods.meapp.features.login.screen.LoginScreen
import com.greatergoods.meapp.features.manualEntry.EntryScreen
import com.greatergoods.meapp.features.metricinfo.MetricInfoScreen
import com.greatergoods.meapp.features.profile.screen.ProfileScreen
import com.greatergoods.meapp.features.scaleDetails.screens.ScaleDetailsScreen
import com.greatergoods.meapp.features.scaleDisplayMetrics.screens.ScaleDisplayMetricsScreen
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
    ScaleDetailsScreen(scaleInfo.scaleId)
  }
  entry<AppRoute.AccountSettings.AppPermissions> { AppPermissionsScreen() }
}

fun EntryProviderBuilder<NavKey>.scaleDetailEntries() {
  entry<AppRoute.ScaleDetails.ScaleMode> { scaleInfo ->
    ScaleModeScreen(scaleInfo.scaleId)
  }
  entry<AppRoute.ScaleDetails.ScaleDisplayMetrics> { scaleInfo ->
    ScaleDisplayMetricsScreen(scaleInfo.scaleId)
  }
  entry<AppRoute.ScaleDetails.ScaleUsers> { scaleInfo ->
    ScaleUserListScreen(scaleInfo.scaleId)
  }
}

fun EntryProviderBuilder<NavKey>.integrationEntries() {
  entry<AppRoute.Integration.IntegrationList> { IntegrationScreen() }
  entry<AppRoute.Integration.HealthConnect> { HealthConnectIntegrationScreen() }
}

fun EntryProviderBuilder<NavKey>.scaleSetupEntries() {
  entry<AppRoute.ScaleSetup.BtWifiScaleSetup> { scaleInfo ->
    BtWifiScaleSetupScreen(scaleInfo.sku)
  }
  entry<AppRoute.ScaleSetup.BtScaleSetup> { scaleInfo ->
    BtScaleSetupScreen(scaleInfo.sku)
  }
  entry<AppRoute.ScaleSetup.LcbtScaleSetup> { scaleInfo ->
    LcbtScaleSetupScreen(scaleInfo.sku)
  }
  entry<AppRoute.ScaleSetup.WifiScaleSetup> { scaleInfo ->
    WifiScaleSetupScreen(scaleInfo.sku)
  }
  entry<AppRoute.ScaleSetup.AppsyncScaleSetup> { scaleInfo ->
    AppsyncScaleSetupScreen(scaleInfo.sku)
  }
}

fun EntryProviderBuilder<NavKey>.dashboardEntries() {
  entry<AppRoute.Dashboard.MetricInfo> { metricInfo ->
    MetricInfoScreen(
      info = metricInfo.info,
      key = metricInfo.key,
      source = metricInfo.source,
    )
  }
}

fun EntryProviderBuilder<NavKey>.historyEntries() {
  entry<AppRoute.History.MonthDetails> { monthDetails ->
    HistoryDetailScreen(monthDetails.month)
  }
}
