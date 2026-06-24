package com.dmdbrands.gurus.weight.features.home.reducer

import com.dmdbrands.gurus.weight.core.config.AppSyncConfig
import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.greatergoods.libs.appsync.model.AppSyncResult
import android.app.Activity
import androidx.compose.runtime.Stable

/**
 * State for HomeScreen.
 */
@Stable
data class HomeState(
  val showAppsync: Boolean = false,
  val isAppSyncPermissionsEnabled: Boolean = false,
  val showWeightOnlyModeBottomSheet: Boolean = false,
  val openWeightOnlyModePopup: Boolean = false,
  val isWeightOnlyModeDismissed: Boolean = false,
  val isBodyMetricsEnabled: Boolean = false,
  val showUnreadFeedIndicator: Boolean = false,
  val shouldAskForReview: Boolean = false,
  val appSyncZoomLevel: Int = AppSyncConfig.DEFAULT_ZOOM,
  val isScanning: Boolean = false,
) : IReducer.State

/**
 * Intents for HomeScreen actions.
 */
sealed interface HomeIntent : IReducer.Intent {
  data class SetShowAppsync(
    val show: Boolean,
  ) : HomeIntent

  data class isAppSyncPermissionsEnabled(
    val enabled: Boolean,
  ) : HomeIntent

  data class CheckAndRequestPermission(
    val onResult: (Boolean) -> Unit
  ) : HomeIntent

  data class HandleAppSyncResult(
    val result: AppSyncResult
  ) : HomeIntent

  data class SetShowWeightOnlyModeBottomSheet(
    val show: Boolean
  ) : HomeIntent

  data class OpenWeightOnlyModePopup(
    val open: Boolean
  ) : HomeIntent

  data object OnWeightOnlyModeEnable : HomeIntent

  data object OnWeightOnlyModeAlertDismiss : HomeIntent

  data class SetWeightOnlyModeDismissed(
    val isDismissed: Boolean
  ) : HomeIntent

  data class SetShowUnreadFeedIndicator(
    val show: Boolean
  ) : HomeIntent

  data class SetShouldAskForReview(
    val shouldAsk: Boolean
  ): HomeIntent

  data class LaunchAppReview(val activity: Activity) : HomeIntent

  data class SetAppSyncZoomLevel(val zoom: Int) : HomeIntent

  /**
   * Starts the AppSync camera scan flow on the ViewModel scope.
   *
   * Carries the [Activity] needed to launch the scan activity. Running on the ViewModel
   * scope (instead of the screen's rememberCoroutineScope) means the scan survives
   * recomposition/disposal after the device has been idle (MOB-710).
   */
  data class StartAppSyncScan(val activity: Activity) : HomeIntent

  data class SetScanning(val scanning: Boolean) : HomeIntent
}

/**
 * Reducer for HomeScreen.
 */
class HomeReducer : IReducer<HomeState, HomeIntent> {
  override fun reduce(
    state: HomeState,
    intent: HomeIntent,
  ): HomeState? =
    when (intent) {
      is HomeIntent.SetShowAppsync -> state.copy(showAppsync = intent.show)
      is HomeIntent.isAppSyncPermissionsEnabled ->
        state.copy(isAppSyncPermissionsEnabled = intent.enabled)

      is HomeIntent.SetShowWeightOnlyModeBottomSheet ->
        state.copy(showWeightOnlyModeBottomSheet = intent.show)

      is HomeIntent.OpenWeightOnlyModePopup ->
        state.copy(openWeightOnlyModePopup = intent.open)

      is HomeIntent.SetWeightOnlyModeDismissed ->
        state.copy(isWeightOnlyModeDismissed = intent.isDismissed)
      is HomeIntent.SetShowUnreadFeedIndicator ->
        state.copy(showUnreadFeedIndicator = intent.show)
      is HomeIntent.SetShouldAskForReview -> state.copy(shouldAskForReview = intent.shouldAsk)
      is HomeIntent.SetAppSyncZoomLevel -> state.copy(appSyncZoomLevel = intent.zoom)
      is HomeIntent.SetScanning -> state.copy(isScanning = intent.scanning)

      else -> state.copy()
    }
}
