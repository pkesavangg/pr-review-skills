package com.dmdbrands.gurus.weight.domain.model.common

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import kotlinx.serialization.Serializable

/**
 * Account-level measurement system (Phase 2 / MOB-377). Distinct from [WeightUnit]
 * (which controls weight *display*); this is the system-wide unit preference returned
 * by the unified account API and required when `productTypes` includes `baby`.
 *
 * @property value The API/storage string value.
 */
@Serializable
enum class MeasurementUnits(
    val value: String,
) {
    METRIC("metric"),
    IMPERIAL_LB_OZ("imperialLbOz"),
    IMPERIAL_LB_DECIMAL("imperialLbDecimal");

    /** Maps to the weight-display [WeightUnit]. */
    fun toWeightUnit(): WeightUnit = when (this) {
        METRIC -> WeightUnit.KG
        IMPERIAL_LB_OZ -> WeightUnit.LB_OZ
        IMPERIAL_LB_DECIMAL -> WeightUnit.LB
    }

    companion object {
        /** Parses an API/storage value (case-insensitive); defaults to [METRIC]. */
        fun fromValue(value: String?): MeasurementUnits =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) }
                ?: run {
                    AppLog.w("MeasurementUnits", "Unknown measurement units '$value', defaulting to metric")
                    METRIC
                }

        /** Derives the account measurement system from a weight-display [WeightUnit]. */
        fun fromWeightUnit(weightUnit: WeightUnit): MeasurementUnits = when (weightUnit) {
            WeightUnit.KG -> METRIC
            WeightUnit.LB_OZ -> IMPERIAL_LB_OZ
            WeightUnit.LB -> IMPERIAL_LB_DECIMAL
        }
    }
}
