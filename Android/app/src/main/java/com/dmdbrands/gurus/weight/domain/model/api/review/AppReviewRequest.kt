package com.dmdbrands.gurus.weight.domain.model.api.review

data class AppReviewRequest(
    val status: String,
    val rating: Int? = null,
    val feedback: String? = null,
    val accountFlagId: String? = null
)
