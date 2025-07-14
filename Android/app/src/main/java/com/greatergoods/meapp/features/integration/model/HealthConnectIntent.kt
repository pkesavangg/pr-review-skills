package com.greatergoods.meapp.features.integration.model

/**
 * Sealed class representing user intents for Health Connect integration.
 */
sealed class HealthConnectIntent {
    data object Connect : HealthConnectIntent()
    data object Finish : HealthConnectIntent()
    data object Skip : HealthConnectIntent()
    data object OpenHealthConnect: HealthConnectIntent()
    data object Exit : HealthConnectIntent()
    data object ConnectSuccess : HealthConnectIntent()
    data object ConnectError : HealthConnectIntent()
    data object ClearError : HealthConnectIntent()
}
