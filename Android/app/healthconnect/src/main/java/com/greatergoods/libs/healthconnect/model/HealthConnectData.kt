package com.greatergoods.libs.healthconnect.model

import com.greatergoods.libs.healthconnect.enum.DataType
import java.time.Instant

/**
 * Represents a single health data entry for Health Connect.
 * @param type The type of health data (e.g., Weight, BloodPressure).
 * @param value The numeric value, if applicable.
 * @param bloodPressure The blood pressure value, if applicable.
 * @param timeStamp The timestamp of the data.
 */
data class HealthConnectData(
    val type: DataType,
    val value: Double? = null,
    val bloodPressure: BloodPressureValue? = null,
    val timeStamp: Instant
)
