package com.greatergoods.meapp.features.metricinfo

import com.greatergoods.meapp.domain.interfaces.IReducer
import com.greatergoods.meapp.domain.model.storage.entry.DashboardMetric
import com.greatergoods.meapp.features.common.model.Stat
import com.greatergoods.meapp.proto.MetricKey

/**
 * UI state for the metric info feature.
 */
data class MetricInfoState(
  val stat: Stat? = null,
  val info: DashboardMetric? = null,
) : IReducer.State

/**
 * Intent for metric info actions.
 */
sealed interface MetricInfoIntent : IReducer.Intent {
  data class SelectSegment(val key: MetricKey) : MetricInfoIntent
  data class SetMetricInfo(val info: DashboardMetric) : MetricInfoIntent
  data class SetStat(val stat: Stat) : MetricInfoIntent
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
    else -> state
  }
}
