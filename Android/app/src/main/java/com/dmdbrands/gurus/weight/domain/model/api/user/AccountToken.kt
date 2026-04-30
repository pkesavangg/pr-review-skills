package com.dmdbrands.gurus.weight.domain.model.api.user

/**
 * Data class for updating account tokens in the repository.
 * @property accountId The ID of the account whose tokens are being updated.
 * @property accessToken The new access token.
 * @property refreshToken The new refresh token.
 * @property expiresAt The expiration timestamp for the access token.
 */
data class AccountToken(
    val accountId: String,
    val accessToken: String?,
    val refreshToken: String?,
    val expiresAt: String?,
)
