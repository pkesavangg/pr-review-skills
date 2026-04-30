package com.greatergoods.libs.healthconnect.model

import com.greatergoods.libs.healthconnect.enums.DataType

/**
 * Options for requesting permissions or performing operations.
 * @param writeTypes Types to request write access for.
 */
data class HealthConnectOptions(
    val writeTypes: Set<DataType>,
)
