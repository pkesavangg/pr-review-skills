package com.dmdbrands.gurus.weight.domain.model.api.auth

/**
 * Request model for changing user password.
 * @property oldPassword The current password.
 * @property newPassword The new password.
 */
data class ChangePasswordRequest(
    val oldPassword: String,
    val newPassword: String,
)
