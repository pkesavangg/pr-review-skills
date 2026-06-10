package com.dmdbrands.gurus.weight.domain.model.api.user

/**
 * Request body for `PATCH /v3/account/measurement-units` (MOB-377).
 *
 * @property measurementUnits One of `metric` / `imperialLbOz` / `imperialLbDecimal`.
 */
data class MeasurementUnitsRequest(
    val measurementUnits: String,
)
