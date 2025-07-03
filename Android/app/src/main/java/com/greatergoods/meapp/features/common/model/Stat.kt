package com.greatergoods.meapp.features.common.model

import com.greatergoods.meapp.proto.MetricKey
import com.greatergoods.meapp.proto.MilestoneKey

/**
 * Union type representing either a MetricKey or MilestoneKey.
 * This is much simpler to work with than the proto oneof approach.
 */
sealed class DashboardKey {
    data class Metric(val key: MetricKey) : DashboardKey()
    data class Milestone(val key: MilestoneKey) : DashboardKey()
}

data class Stat(
    val label: String,
    val value: String?,
    val unit: String? = "",
    val icon: Int? = null,
    val key: DashboardKey,
    val valuePrefix: String? = null
)


