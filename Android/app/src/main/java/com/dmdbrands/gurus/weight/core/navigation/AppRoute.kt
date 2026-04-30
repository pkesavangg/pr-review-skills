package com.dmdbrands.gurus.weight.core.navigation

import androidx.navigation3.runtime.NavKey
import com.dmdbrands.gurus.weight.domain.enums.MetricKey
import com.dmdbrands.gurus.weight.domain.model.storage.entry.DashboardMetric
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.BtWifiSetupStep
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.LcbtScaleSetupStep
import com.dmdbrands.gurus.weight.features.common.model.ScaleInfo
import com.dmdbrands.gurus.weight.features.metricinfo.MetricInfoSource
import com.dmdbrands.library.ggbluetooth.model.GGBTUser
import com.example.nav3integration.PublicRoute
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 * Defines all navigation routes for the app using sealed classes for type safety and serialization.
 */
sealed class AppRoute : NavKey {
  /**
   * Initial navigation routes (e.g., splash, onboarding).
   */
  @Serializable
  sealed class Init :
    AppRoute(),
    PublicRoute {
    @Serializable
    data object Loading : Init()
  }

  @Serializable
  data object Home : AppRoute()

  @Serializable
  data object App : AppRoute()

  /**
   * Main navigation routes for the app.
   */
  @Serializable
  sealed class Main : AppRoute() {
    @Serializable
    data object Dashboard : Main()

    @Serializable
    data object Entry : Main()

    @Serializable
    data object History : Main()

    @Serializable
    data object Settings : Main()

    @Serializable
    data object AppSync : Main()
  }

  @Serializable
  sealed class History : AppRoute() {
    @Serializable

    data class MonthDetails(
      val month: String,
    ) : AppRoute()
  }

  /**
   * Authentication-related navigation routes.
   */

  @Serializable
  sealed class Auth : AppRoute(), PublicRoute {
    @Serializable
    data object Landing : Auth()

    @Serializable
    data class Login(
      val email: String? = null,
    ) : Auth()

    @Serializable
    data object Signup : Auth()

    @Serializable
    data object MultiAccountLanding : Auth()
  }

  /**
   * Profile-related navigation routes.
   */
  @Serializable
  sealed class AccountSettings : AppRoute() {
    @Serializable
    data object ChangePassword : AccountSettings()

    @Serializable
    data object Profile : AccountSettings()

    @Serializable
    data object MyAccounts : AccountSettings()

    @Serializable
    data object Weightless : AccountSettings()

    @Serializable
    data object AddEditScales : AccountSettings()

    @Serializable
    data object ChooseScale : AccountSettings()

    @Serializable
    data object Goal : AccountSettings()

    @Serializable
    data object HelpScreen : AccountSettings()

    @Serializable
    data object DebugMenu : AccountSettings()

    /** Screen to choose a paired scale (e.g. 0412) when multiple scales; send log for selected scale. */
    @Serializable
    data object ScaleLogsPicker : AccountSettings()

    @Serializable
    data object AppPermissions : AccountSettings()

    @Serializable
    data class ScaleDetails(
      val scaleId: String,
    ) : AccountSettings()
  }

  @Serializable
  sealed class ScaleDetails : AppRoute() {
    @Serializable
    data class ScaleMode(
      val scaleId: String,
    ) : ScaleDetails()

    @Serializable
    data class ScaleDisplayMetrics(
      val scaleId: String,
    ) : ScaleDetails()

    @Serializable
    data class ScaleUsers(
      val scaleId: String,
    ) : ScaleDetails()
  }

  @Serializable
  sealed class Integration : AppRoute() {
    @Serializable
    data object IntegrationList : Integration()

    @Serializable
    data object HealthConnect : Integration()
  }

  @Serializable
  sealed class Feed : AppRoute() {
    @Serializable
    data object FeedMessages : Feed()

    @Serializable
    data object FeedMessageSetting : Feed()

    @Serializable
    data object FeedLanding : Feed()

    @Serializable
    data object FeedFAQ : Feed()
  }

  @Serializable
  sealed class ScaleSetup : AppRoute() {
    @Serializable
    data class BtWifiScaleSetup(
      val sku: String,
      val initialStep: BtWifiSetupStep = BtWifiSetupStep.SCALE_INFO,
      val broadcastId: String? = null,
      val userList: List<@Contextual GGBTUser>? = null
    ) : ScaleSetup()

    @Serializable
    data class BtScaleSetup(
      val sku: String,
      val scaleInfo: ScaleInfo? = null
    ) : ScaleSetup()

    @Serializable
    data class LcbtScaleSetup(
      val sku: String,
      val broadcastId: String? = null,
      val initialStep: LcbtScaleSetupStep = LcbtScaleSetupStep.SCALE_INFO,
      val scaleInfo: ScaleInfo? = null
    ) : ScaleSetup()

    @Serializable
    data class WifiScaleSetup(
      val sku: String,
      val wifiSetupType: String = "first",
      val scaleInfo: ScaleInfo? = null
    ) : ScaleSetup()

    @Serializable
    data class AppsyncScaleSetup(
      val sku: String
    ) : ScaleSetup()
  }

  @Serializable
  sealed class Dashboard : AppRoute() {
    @Serializable
    data class MetricInfo(
      val info: DashboardMetric,
      val key: MetricKey = MetricKey.BMI,
      val source: MetricInfoSource = MetricInfoSource.WEEK
    ) : Dashboard()
  }
}

