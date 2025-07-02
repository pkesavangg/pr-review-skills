package com.greatergoods.meapp.features.common.model

import com.greatergoods.meapp.features.common.enums.MetricKey

data class Stat(
    val label: String,
    val value: String?,
    val unit: String? = "",
    val icon: Int? = null,
    val key: DashboardKey,
    val valuePrefix: String? = null
)
