package com.dmdbrands.gurus.weight.domain.model

/**
 * Data class representing an account flag from the server.
 * Account flags are used to trigger various user prompts and actions.
 *
 * @property id Unique identifier for the flag
 * @property type Type of the flag (e.g., "scale-review-ask", "app-rate-ask")
 * @property trigger When this flag should be triggered (e.g., "login", "entry")
 * @property data Additional data associated with the flag
 */
data class AccountFlag(
    val id: String,
    val type: String,
    val trigger: String,
    val data: Any? = null
)

/**
 * Data class representing an app review request.
 * Used to trigger app review flows and collect user feedback.
 *
 * @property screen The screen/component to show for the review
 * @property sku The SKU of the product being reviewed
 * @property flagId The ID of the account flag that triggered this review
 * @property rating Optional rating value provided by the user
 */
data class AppReview(
    val screen: String,
    val sku: String? = null,
    val flagId: String? = null,
    val rating: Int? = null
)
