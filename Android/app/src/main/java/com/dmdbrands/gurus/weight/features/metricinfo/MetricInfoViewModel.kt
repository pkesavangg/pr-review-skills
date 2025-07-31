package com.dmdbrands.gurus.weight.features.metricinfo

import com.dmdbrands.gurus.weight.domain.model.storage.entry.DashboardMetric
import com.dmdbrands.gurus.weight.features.common.helper.StatHelper
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.dmdbrands.gurus.weight.proto.MetricKey
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel

@HiltViewModel(
  assistedFactory = MetricInfoViewModel.Factory::class,
)
class MetricInfoViewModel @AssistedInject constructor(
  @Assisted val info: DashboardMetric,
  @Assisted val key: MetricKey
) : BaseIntentViewModel<MetricInfoState, MetricInfoIntent>(
  reducer = MetricInfoReducer(),
) {

  @AssistedFactory
  interface Factory {
    fun create(info: DashboardMetric, key: MetricKey = MetricKey.WEIGHT): MetricInfoViewModel
  }

  init {
    handleIntent(MetricInfoIntent.SetMetricInfo(info))
    handleIntent(MetricInfoIntent.SelectSegment(key))
  }

  override fun provideInitialState(): MetricInfoState = MetricInfoState()

  override fun handleIntent(intent: MetricInfoIntent) {
    when (intent) {
      is MetricInfoIntent.SelectSegment -> {
        val stat = StatHelper.getMetricValue(info, intent.key)
        handleIntent(MetricInfoIntent.SetStat(stat))
      }

      is MetricInfoIntent.OpenResource -> {
        openInAppBrowser(intent.resource)
      }

      else -> Unit
    }
    super.handleIntent(intent)
  }
}
