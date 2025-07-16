package com.greatergoods.meapp.domain.model.integrations

import com.greatergoods.libs.healthconnect.enums.DataType

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
    val operationType: IntegrationOperationType,
    val scopes: IntegrationData,
    val isCurrentDeviceDeleted: Boolean = false
)

/**
 * Types of integrations supported.
 */
enum class IntegrationType {
    HEALTH_CONNECT,
    HEALTH_KIT;

    companion object {
        fun fromString(value: String): IntegrationType {
            return when (value.lowercase()) {
                "health_connect", "healthconnect" -> HEALTH_CONNECT
                "health_kit", "healthkit" -> HEALTH_KIT
                else -> throw IllegalArgumentException("Unknown integration type: $value")
            }
        }
    }
}

/**
 * Types of operations for integration management.
 */
enum class IntegrationOperationType {
    SAVE,
    REMOVE;

    companion object {
        fun fromString(value: String): IntegrationOperationType {
            return when (value.lowercase()) {
                "save" -> SAVE
                "remove" -> REMOVE
                else -> throw IllegalArgumentException("Unknown operation type: $value")
            }
        }
    }
}
