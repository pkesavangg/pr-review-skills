package com.greatergoods.meapp.features.integration.model

/**
 * Sealed class representing user intents for Health Connect integration.
 */
sealed class HealthConnectIntent {
  data object ConfirmExitSetup : HealthConnectIntent()
    data object ConnectSuccess : HealthConnectIntent()
    data object ConnectError : HealthConnectIntent()
    data object AppResumed : HealthConnectIntent()
    data object SetAlertPresented : HealthConnectIntent()
    data object ClearAlertPresented : HealthConnectIntent()
    data object SetHealthConnectOpened : HealthConnectIntent()
    data object ClearHealthConnectOpened : HealthConnectIntent()
    data class UpdateSlide(val slide: Int) : HealthConnectIntent()
    data class PrimaryAction(val label: HealthConnectAction) : HealthConnectIntent()
    data class SecondaryAction(val label: HealthConnectAction) : HealthConnectIntent()
}
