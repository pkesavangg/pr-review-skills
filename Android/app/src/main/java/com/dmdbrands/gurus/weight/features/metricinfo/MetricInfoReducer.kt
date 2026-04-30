package com.dmdbrands.gurus.weight.features.metricinfo

import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.enums.MetricKey
import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.storage.entry.DashboardMetric
import com.dmdbrands.gurus.weight.features.common.model.Stat

/**
 * UI state for the metric info feature.
 */
data class MetricInfoState(
  val stat: Stat? = null,
  val info: DashboardMetric? = null,
  val selectedMetricIndex: Int = 0,
  val isHeartRateOff: Boolean = false,
  val dashboardType: DashboardType? = null,
) : IReducer.State

/**
 * Intent for metric info actions.
 */
sealed interface MetricInfoIntent : IReducer.Intent {
  data class SelectSegment(val key: MetricKey) : MetricInfoIntent
  data class SetMetricInfo(val info: DashboardMetric) : MetricInfoIntent
  data class SetStat(val stat: Stat) : MetricInfoIntent
  data class SetSelectedIndex(val index: Int) : MetricInfoIntent
  data class SetHeartRateStatus(val heartRate: Boolean) : MetricInfoIntent
  data class SetDashboardType(val dashboardType: DashboardType) : MetricInfoIntent
  data class OpenResource(val resource: String) : MetricInfoIntent
  object UpdateScaleMode : MetricInfoIntent
}

/**
 * Reducer for the metric info state.
 */
class MetricInfoReducer : IReducer<MetricInfoState, MetricInfoIntent> {
  override fun reduce(
    state: MetricInfoState,
    intent: MetricInfoIntent,
  ): MetricInfoState? = when (intent) {
    is MetricInfoIntent.SetStat -> state.copy(
      stat = intent.stat,
    )

    is MetricInfoIntent.SetMetricInfo -> state.copy(
      info = intent.info,
    )

    is MetricInfoIntent.SetSelectedIndex -> state.copy(
      selectedMetricIndex = intent.index,
    )
    is MetricInfoIntent.SetHeartRateStatus -> state.copy(
      isHeartRateOff = intent.heartRate,
    )

    is MetricInfoIntent.SetDashboardType -> state.copy(
      dashboardType = intent.dashboardType,
    )

    else -> state
  }
}
