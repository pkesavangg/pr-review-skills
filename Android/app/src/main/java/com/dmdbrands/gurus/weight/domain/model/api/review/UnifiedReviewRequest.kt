package com.dmdbrands.gurus.weight.domain.model.api.review

/**
 * Request body for `POST /v3/review/` (MOB-378).
 *
 * The unified review endpoint replaces the separate `/review/app` and `/review/scale`
 * endpoints. [reviewType] determines which product the review applies to; [sku] is
 * required for scale/monitor types.
 */
data class UnifiedReviewRequest(
    // Required
    val reviewType: String,       // "app" | "scale" | "monitor"
    val status: String,

    // Conditional: required unless status = "exitA"
    val rating: Int? = null,

    // Conditional: required for scale / monitor
    val sku: String? = null,

    // Optional
    val feedback: String? = null,
    val flagId: String? = null,
)
