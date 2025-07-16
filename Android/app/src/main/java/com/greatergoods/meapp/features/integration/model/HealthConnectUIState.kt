package com.greatergoods.meapp.features.integration.model

import com.greatergoods.libs.healthconnect.enums.HealthConnectPermissionStatus

data class HealthConnectUiState(
  val healthConnectSetupState: HealthConnectSetup = HealthConnectSetup.NONE,
  val currentSlide: Int = 0,
  val isHealthConnectAvailable: Boolean = false,
  val isLoading: Boolean = false,
  val permissionStatus: HealthConnectPermissionStatus = HealthConnectPermissionStatus.NONE,
  val isOutOfSync: Boolean = false,
  val errorMessage: String? = null,
  val actionButtons: ActionButtons = ActionButtons(),
  val alertPresented: Boolean = false,
  val isHealthConnectOpened: Boolean = false,
  val pageLoadFrom: PageLoadFrom = PageLoadFrom()
)

data class ActionButtons(
  val primary: HealthConnectAction? = null,
  val secondary: HealthConnectAction? = null
)

data class PageLoadFrom(
  val dashBoard: Boolean = false,
  val integrationFailed: Boolean = false
)
