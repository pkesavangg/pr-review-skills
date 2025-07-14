package com.greatergoods.meapp.features.integration.model

data class HealthConnectUiState(
  val healthConnectSetupState: HealthConnectSetup = HealthConnectSetup.NONE,
  val isHealthConnectAvailable: Boolean = false,
  val isLoading: Boolean = false,
  val permissionStatus: HealthConnectPermissionStatus = HealthConnectPermissionStatus.NONE,
  val isOutOfSync: Boolean = false,
  val errorMessage: String? = null,
  val actionButtons: ActionButtons = ActionButtons()
)

data class ActionButtons(
  val primary: HealthConnectAction? = null,
  val secondary: HealthConnectAction? = null
)
