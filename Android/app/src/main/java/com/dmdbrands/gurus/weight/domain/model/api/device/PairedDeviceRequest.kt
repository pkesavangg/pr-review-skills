package com.dmdbrands.gurus.weight.domain.model.api.device

/**
 * Request body for `POST /v3/paired-device/` and `PATCH /v3/paired-device/{deviceId}` (MOB-378).
 *
 * The unified paired-device API replaces the per-product pairing endpoints. [deviceType]
 * is required so the server knows which product category the device belongs to.
 */
data class PairedDeviceRequest(
    // Required
    val deviceType: String,       // "weight_scale" / "baby_scale" / "bpm"
    val type: String,             // connection protocol (btWifiR4, bluetooth, wifi, …)
    val nickname: String,
    val sku: String,

    // Optional
    val mac: String? = null,
    val broadcastId: Long? = null,
    val password: Long? = null,
    val userNumber: Int? = null,
    val name: String? = null,
    val peripheralIdentifier: String? = null,
    val scaleToken: String? = null,
)
