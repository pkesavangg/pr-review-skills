package com.dmdbrands.gurus.weight.features.metricinfo

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.domain.enums.MetricKey
import com.dmdbrands.gurus.weight.domain.model.storage.entry.DashboardMetric
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.features.common.enums.ScaleSetupType
import com.dmdbrands.gurus.weight.features.common.helper.ScaleDataHelper.toScaleInfo
import com.dmdbrands.gurus.weight.features.common.helper.StatHelper
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel(
  assistedFactory = MetricInfoViewModel.Factory::class,
)
class MetricInfoViewModel @AssistedInject constructor(
  @Assisted val info: DashboardMetric,
  @Assisted val key: MetricKey,
  private val deviceService: IDeviceService,
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
    updateHeartRateStatus()
  }

  override fun provideInitialState(): MetricInfoState = MetricInfoState()

  override fun handleIntent(intent: MetricInfoIntent) {
    when (intent) {
      is MetricInfoIntent.SelectSegment -> {
        val stat = StatHelper.getMetricValue(info, intent.key)
        handleIntent(MetricInfoIntent.SetStat(stat))
        // Update selected index based on the key
        val metricKeys = getFilteredMetricKeys()
        val index = metricKeys.indexOfFirst { it == intent.key }
        if (index >= 0) {
          handleIntent(MetricInfoIntent.SetSelectedIndex(index))
        }
      }

      is MetricInfoIntent.SetSelectedIndex -> {
        val metricKeys = MetricKey.getAllMetrics()
        if (intent.index in metricKeys.indices) {
          val selectedKey = metricKeys[intent.index]
          val selectedStat = StatHelper.getMetricValue(info, selectedKey)
          handleIntent(MetricInfoIntent.SetStat(selectedStat))
        }
      }

      is MetricInfoIntent.OpenResource -> {
        openInAppBrowser(intent.resource)
      }

      is MetricInfoIntent.UpdateScaleMode -> {
        updateHeartRateStatus()
      }

      else -> Unit
    }
    super.handleIntent(intent)
  }

  private fun updateHeartRateStatus() {
    viewModelScope.launch {
      // Collect saved scales from DeviceService
      deviceService.pairedScales.collect { devices ->
        val heartRateEnabled = devices.any {
          it.toScaleInfo().setupType == ScaleSetupType.BtWifiR4
            && it.preferences?.shouldMeasurePulse == false && it.preferences.shouldMeasureImpedance == true
            && !it.isWeighOnlyModeEnabledByOthers
        }
        handleIntent(MetricInfoIntent.SetHeartRateStatus(heartRateEnabled))
      }
    }
  }

  private fun onUpdateScaleMode() {
    viewModelScope.launch {
      val scales = deviceService.pairedScales.first()
      val scale = scales.firstOrNull { it ->
        it.toScaleInfo().setupType == ScaleSetupType.BtWifiR4
          && it.preferences?.shouldMeasurePulse == false && it.preferences.shouldMeasureImpedance == true
          && !it.isWeighOnlyModeEnabledByOthers
      }
      scale?.id?.let { scaleId ->
        navigationService.navigateTo(AppRoute.ScaleDetails.ScaleMode(scaleId))
      }
    }
  }
}
