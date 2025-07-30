package com.dmdbrands.gurus.weight.domain.model.api.review

data class AccountFlagResponse(
    val id: String,
    val type: String,
    val trigger: String,
    val data: Any? = null
)
