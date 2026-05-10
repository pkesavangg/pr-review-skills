package com.dmdbrands.gurus.weight.app.viewmodel

import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.proto.ThemeMode

/**
 * UI state for the app, holding theme mode and FCM token.
 *
 * @property themeMode The current theme mode.
 * @property fcmToken The current FCM token.
 */
data class AppState(
  val fcmToken: String = "",
  val themeMode: ThemeMode = ThemeMode.SYSTEM,
  val isScaleDiscovered: Boolean = false,
  val hasScanStarted: Boolean = false,
  val sku: String = "0412",
  val unreadFeedCount: Int = 0,
  val showUnreadFeedIndication: Boolean = false,
  val scaleDiscoveredTimestamp: Long? = null,
) : IReducer.State

/**
 * Intent for the app, defining actions to change theme mode and FCM token.
 */
sealed interface AppIntent : IReducer.Intent {
  data class SetScaleDiscovered(val isScaleDiscovered: Boolean) : AppIntent
  data class SetSku(val sku: String) : AppIntent
  data class SetScanStatus(val hasScanStarted: Boolean) : AppIntent
  data class SetThemeMode(val themeMode: ThemeMode) : AppIntent

  data object OnPopUpConnect : AppIntent
  data object OnPopUpDismiss : AppIntent
  data class SetUnreadFeedCount(val count: Int) : AppIntent
  data class SetShowUnreadFeedIndication(val show: Boolean) : AppIntent
}

/**
 * Reducer for the app state, handling intents to update theme mode and FCM token.
 *
 * @property initialState The initial state of the app.
 */
class AppReducer() : IReducer<AppState, AppIntent> {

  override fun reduce(
    state: AppState,
    intent: AppIntent
  ): AppState? {
    return when (intent) {
      is AppIntent.SetSku -> state.copy(sku = intent.sku)
      is AppIntent.SetScaleDiscovered -> state.copy(
        isScaleDiscovered = intent.isScaleDiscovered,
        scaleDiscoveredTimestamp = if (intent.isScaleDiscovered) System.currentTimeMillis() else null,
      )
      is AppIntent.SetScanStatus -> state.copy(hasScanStarted = intent.hasScanStarted)
      is AppIntent.SetThemeMode -> state.copy(themeMode = intent.themeMode)
      is AppIntent.SetUnreadFeedCount -> state.copy(unreadFeedCount = intent.count)
      is AppIntent.SetShowUnreadFeedIndication -> state.copy(showUnreadFeedIndication = intent.show)
      else -> state
    }
  }
}

