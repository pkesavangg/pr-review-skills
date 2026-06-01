package com.dmdbrands.gurus.weight.domain.model.api.auth

/**
 * Request body for `POST /v3/account/email-check` (MOB-377) — callable pre-signup,
 * no auth required.
 */
data class EmailCheckRequest(
    val email: String,
)

/**
 * Response for `POST /v3/account/email-check`.
 *
 * @property isAvailable true when the email is not already registered.
 */
data class EmailCheckResponse(
    val isAvailable: Boolean,
)
