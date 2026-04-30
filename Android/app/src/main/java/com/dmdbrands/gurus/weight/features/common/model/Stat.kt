package com.dmdbrands.gurus.weight.features.common.model

import com.dmdbrands.gurus.weight.domain.enums.MetricKey
import com.dmdbrands.gurus.weight.domain.enums.MilestoneKey

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
  val valuePrefix: String? = null,
  val valueSuffix: String? = null
) {
  fun getDisplayValue(): String? {
    if (value == null) return null
    val prefix = valuePrefix?.let { "$it " } ?: ""
    val suffix = valueSuffix?.let { " $it" } ?: ""
    return "$prefix$value$suffix".trim()
  }
}


