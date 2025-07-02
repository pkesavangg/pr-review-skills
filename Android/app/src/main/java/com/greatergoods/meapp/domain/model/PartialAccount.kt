package com.greatergoods.meapp.domain.model

/**
 * Data class for partial account updates.
 * Only the fields that need to be updated should be provided.
 */
data class PartialAccount(
    val firstName: String? = null,
    val lastName: String? = null,
    val dob: String? = null,
    val email: String? = null,
    val expiresAt: String? = null,
    val fcmToken: String? = null,
    val gender: String? = null,
    val isActiveAccount: Boolean? = null,
    val isLoggedIn: Boolean? = null,
    val isExpired: Boolean? = null,
    val isSynced: Boolean? = null,
    val lastActiveTime: String? = null,
    val zipcode: String? = null,
)
