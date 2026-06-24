package com.dmdbrands.gurus.weight.domain.model.api.device

/**
 * Raised when a `/v3/paired-device/` API call returns an unsuccessful HTTP response.
 *
 * Carries the HTTP status [code] so the service/UI layer can distinguish auth (401),
 * not-found (404), conflict (409) and server (5xx) failures and react accordingly
 * (token refresh vs. retry vs. surfaced message) instead of catching a bare [Exception].
 */
class DeviceApiException(
  val code: Int,
  message: String,
) : Exception(message)
