package com.greatergoods.meapp.app.viewmodel

import com.greatergoods.meapp.domain.interfaces.IReducer
import com.greatergoods.meapp.proto.ThemeMode

/**
 * UI state for the app, holding theme mode and FCM token.
 *
 * @property themeMode The current theme mode.
 * @property fcmToken The current FCM token.
 */
data class AppState(
  val fcmToken: String = "",
  val themeMode: ThemeMode = ThemeMode.SYSTEM,
  val isScaleDiscovered: Boolean = false
) : IReducer.State

/**
 * Intent for the app, defining actions to change theme mode and FCM token.
 */
sealed interface AppIntent : IReducer.Intent {
  data class SetScaleDiscovered(val isScaleDiscovered: Boolean) : AppIntent

  data object OnPopUpConnect : AppIntent
  data object OnPopUpDismiss : AppIntent
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
      is AppIntent.SetScaleDiscovered -> return state.copy(isScaleDiscovered = intent.isScaleDiscovered)
      else -> state
    }
  }
}

