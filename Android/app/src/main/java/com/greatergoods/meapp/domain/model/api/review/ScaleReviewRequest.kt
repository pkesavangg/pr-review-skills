package com.greatergoods.meapp.domain.model.api.review

data class ScaleReviewRequest(
    val sku: String,
    val status: String,
    val rating: Int? = null,
    val feedback: String? = null,
    val flagId: String? = null
)

