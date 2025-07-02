package com.greatergoods.meapp.features.historyDetail.modal

import com.greatergoods.meapp.proto.DashboardKey

data class Metric(
    val label: String,
    val value: String?,
    val unit: String,
    val icon: Int,
    val key: DashboardKey
)
