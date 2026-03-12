package com.dmdbrands.gurus.weight.app.components

import androidx.navigation3.runtime.EntryProviderBuilder
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.features.MyAccounts.screen.MyAccountsScreen
import com.dmdbrands.gurus.weight.features.ScaleSetup.screens.AppsyncScaleSetupScreen
import com.dmdbrands.gurus.weight.features.ScaleSetup.screens.BtScaleSetupScreen
import com.dmdbrands.gurus.weight.features.ScaleSetup.screens.BtWifiScaleSetupScreen
import com.dmdbrands.gurus.weight.features.ScaleSetup.screens.LcbtScaleSetupScreen
import com.dmdbrands.gurus.weight.features.ScaleSetup.screens.WifiScaleSetupScreen
import com.dmdbrands.gurus.weight.features.ScaleUsers.screens.ScaleUserListScreen
import com.dmdbrands.gurus.weight.features.addScale.screens.AddScaleScreen
import com.dmdbrands.gurus.weight.features.addScale.screens.ChooseScaleScreen
import com.dmdbrands.gurus.weight.features.appPermissions.AppPermissionsScreen
import com.dmdbrands.gurus.weight.features.appSync.AppSync
import com.dmdbrands.gurus.weight.features.changePassword.ChangePasswordScreen
import com.dmdbrands.gurus.weight.features.dashboard.DashboardScreen
import com.dmdbrands.gurus.weight.features.debugMenu.screen.DebugMenuScreen
import com.dmdbrands.gurus.weight.features.debugMenu.screen.ScaleLogsPickerScreen
import com.dmdbrands.gurus.weight.features.feed.FeedFAQScreen
import com.dmdbrands.gurus.weight.features.feed.FeedLandingScreen
import com.dmdbrands.gurus.weight.features.feedMessages.AppFeedMessagesScreen
import com.dmdbrands.gurus.weight.features.feedMessages.AppFeedMessagesSettingsScreen
import com.dmdbrands.gurus.weight.features.goal.screen.GoalScreen
import com.dmdbrands.gurus.weight.features.help.screen.HelpScreen
import com.dmdbrands.gurus.weight.features.history.HistoryScreen
import com.dmdbrands.gurus.weight.features.historyDetail.HistoryDetailScreen
import com.dmdbrands.gurus.weight.features.integration.HealthConnectIntegrationScreen
import com.dmdbrands.gurus.weight.features.integration.screen.IntegrationScreen
import com.dmdbrands.gurus.weight.features.landing.screen.LandingScreen
import com.dmdbrands.gurus.weight.features.landing.screen.MultiAccountLandingScreen
import com.dmdbrands.gurus.weight.features.login.screen.LoginScreen
import com.dmdbrands.gurus.weight.features.manualEntry.EntryScreen
import com.dmdbrands.gurus.weight.features.metricinfo.MetricInfoScreen
import com.dmdbrands.gurus.weight.features.profile.screen.ProfileScreen
import com.dmdbrands.gurus.weight.features.scaleDetails.screens.ScaleDetailsScreen
import com.dmdbrands.gurus.weight.features.scaleDisplayMetrics.screens.ScaleDisplayMetricsScreen
import com.dmdbrands.gurus.weight.features.scaleMode.screens.ScaleModeScreen
import com.dmdbrands.gurus.weight.features.settings.SettingsScreen
import com.dmdbrands.gurus.weight.features.signup.SignupScreen
import com.dmdbrands.gurus.weight.features.forceUpdate.ForceUpdateScreen
import com.dmdbrands.gurus.weight.features.weightless.screen.WeightlessScreen

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
  entry<AppRoute.AccountSettings.ScaleLogsPicker> { ScaleLogsPickerScreen() }
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
    BtWifiScaleSetupScreen(scaleInfo.sku, scaleInfo.initialStep, scaleInfo.broadcastId, scaleInfo.userList)
  }
  entry<AppRoute.ScaleSetup.BtScaleSetup> { scaleInfo ->
    BtScaleSetupScreen(scaleInfo.sku, scaleInfo.scaleInfo)
  }
  entry<AppRoute.ScaleSetup.LcbtScaleSetup> { scaleInfo ->
    LcbtScaleSetupScreen(scaleInfo.sku,scaleInfo = scaleInfo.scaleInfo, scaleInfo.broadcastId, scaleInfo.initialStep, )
  }
  entry<AppRoute.ScaleSetup.WifiScaleSetup> { scaleInfo ->
    WifiScaleSetupScreen(scaleInfo.sku, scaleInfo.wifiSetupType, scaleInfo.scaleInfo)
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

fun EntryProviderBuilder<NavKey>.feedMessagesEntries() {
  entry<AppRoute.Feed.FeedMessages> { AppFeedMessagesScreen() }
  entry<AppRoute.Feed.FeedMessageSetting> { AppFeedMessagesSettingsScreen() }
  entry<AppRoute.Feed.FeedLanding> { FeedLandingScreen() }
  entry<AppRoute.Feed.FeedFAQ> { FeedFAQScreen() }
}

fun EntryProviderBuilder<NavKey>.forceUpdateEntries() {
  entry<AppRoute.ForceUpdate> { ForceUpdateScreen() }
}
