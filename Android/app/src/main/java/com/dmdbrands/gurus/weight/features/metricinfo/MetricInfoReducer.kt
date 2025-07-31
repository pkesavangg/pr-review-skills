package com.dmdbrands.gurus.weight.features.metricinfo

import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.storage.entry.DashboardMetric
import com.dmdbrands.gurus.weight.features.common.model.Stat
import com.dmdbrands.gurus.weight.proto.MetricKey

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
  data class OpenResource(val resource: String) : MetricInfoIntent
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
