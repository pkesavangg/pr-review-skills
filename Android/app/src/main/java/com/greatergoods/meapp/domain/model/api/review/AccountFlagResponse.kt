package com.greatergoods.meapp.domain.model.api.review

data class AccountFlagResponse(
    val id: String,
    val type: String,
    val trigger: String,
    val data: Any? = null
)
