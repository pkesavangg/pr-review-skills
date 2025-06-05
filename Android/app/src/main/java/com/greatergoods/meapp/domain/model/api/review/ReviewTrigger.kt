package com.greatergoods.meapp.domain.model.api.review

data class ReviewTrigger(
    val sku: String? = null,
    val dateInterval: Int? = null,
    val entryInterval: Int? = null,
    val triggerDate: String? = null,
    val entryTrigger: Int? = null
)
