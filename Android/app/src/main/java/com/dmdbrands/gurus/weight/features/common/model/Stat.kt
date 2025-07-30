package com.dmdbrands.gurus.weight.features.common.model

import com.dmdbrands.gurus.weight.proto.MetricKey
import com.dmdbrands.gurus.weight.proto.MilestoneKey

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
) {
  fun getDisplayValue(): String? {
    val prefix = valuePrefix ?: ""
    return if (value != null) {
      "$prefix $value"
    } else {
      null
    }
  }
}


