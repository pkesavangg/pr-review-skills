package com.greatergoods.meapp.domain.model.integrations

/**
 * Data class for HealthConnectData (define as needed)
 */

/**
 * Represents integration configuration data.
 */
data class IntegrationData(
  val deviceId: String,
  val type: String,
  val preferences: IntegrationPreferences? = null
)

/**
 * Represents integration preferences/settings.
 */
data class IntegrationPreferences(
    val scopes: List<String> = emptyList()
)

/**
 * Represents integrated device information for server operations.
 */
data class IntegratedDeviceInfo(
    val operationType: String,
    val scopes: IntegrationData,
    var isCurrentDeviceDeleted: Boolean = false
)

/**
 * Types of integrations supported.
 */
enum class IntegrationType(val value: String){
    HEALTH_CONNECT("healthconnect"),
    HEALTH_KIT("healthkit");
}

/**
 * Types of operations for integration management.
 */
enum class IntegrationOperationType(val value: String) {
    SAVE("save"),
    REMOVE("remove")
}
