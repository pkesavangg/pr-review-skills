package com.greatergoods.meapp.features.home.reducer

import com.greatergoods.libs.appsync.model.AppSyncResult
import com.greatergoods.meapp.domain.interfaces.IReducer

/**
 * State for HomeScreen.
 */
data class HomeState(
  val showAppsync: Boolean = false,
  val isAppSyncPermissionsEnabled: Boolean = false,
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

      else -> state.copy()
    }
}
