package com.dmdbrands.gurus.weight.features.metricinfo

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.enums.MetricKey
import com.dmdbrands.gurus.weight.domain.model.storage.entry.DashboardMetric
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
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
  private val accountRepository: IAccountRepository,
) : BaseIntentViewModel<MetricInfoState, MetricInfoIntent>(
  reducer = MetricInfoReducer(),
) {

  @AssistedFactory
  interface Factory {
    fun create(info: DashboardMetric, key: MetricKey = MetricKey.WEIGHT): MetricInfoViewModel
  }

  init {
    handleIntent(MetricInfoIntent.SetMetricInfo(info))
    updateHeartRateStatus()
    loadDashboardTypeAndSelectSegment()
  }

  override fun provideInitialState(): MetricInfoState = MetricInfoState()

  override fun handleIntent(intent: MetricInfoIntent) {
    when (intent) {
      is MetricInfoIntent.SelectSegment -> {
        val dashboardType = state.value.dashboardType ?: DashboardType.DASHBOARD_12_METRICS
        val metricKeys = getFilteredMetricKeys(dashboardType)
        val index = metricKeys.indexOfFirst { it == intent.key }

        if (index >= 0) {
          // Key is in filtered list, use it
          val stat = StatHelper.getMetricValue(info, intent.key)
          handleIntent(MetricInfoIntent.SetStat(stat))
          handleIntent(MetricInfoIntent.SetSelectedIndex(index))
        } else {
          // Key is not in filtered list, fallback to first metric in filtered list
          val firstMetricKey = metricKeys.firstOrNull()
          if (firstMetricKey != null) {
            val stat = StatHelper.getMetricValue(info, firstMetricKey)
            handleIntent(MetricInfoIntent.SetStat(stat))
            handleIntent(MetricInfoIntent.SetSelectedIndex(0))
            AppLog.d("MetricInfoViewModel", "Key ${intent.key} not in filtered list, using first metric: $firstMetricKey")
          }
        }
      }

      is MetricInfoIntent.SetSelectedIndex -> {
        val dashboardType = state.value.dashboardType ?: DashboardType.DASHBOARD_12_METRICS
        val metricKeys = getFilteredMetricKeys(dashboardType)
        if (intent.index in metricKeys.indices) {
          val selectedKey = metricKeys[intent.index]
          val selectedStat = StatHelper.getMetricValue(info, selectedKey)
          handleIntent(MetricInfoIntent.SetStat(selectedStat))
        }
      }

      is MetricInfoIntent.SetDashboardType -> {
        // No-op, handled in reducer
      }

      is MetricInfoIntent.OpenResource -> {
        openInAppBrowser(intent.resource)
      }

      is MetricInfoIntent.UpdateScaleMode -> {
        onUpdateScaleMode()
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

  /**
   * Handles navigation when user clicks update button for heart rate.
   * If one R4 scale has heart rate ON, navigate to that scale's detail screen.
   * If multiple R4 scales have heart rate OFF, navigate to My Scales screen.
   * If only one R4 scale has heart rate OFF, navigate to that scale's detail screen.
   */
  private fun onUpdateScaleMode() {
    viewModelScope.launch {
      try {
        val scales = deviceService.pairedScales.first()

        // Filter R4 scales
        val r4Scales = scales.filter {
          it.toScaleInfo().setupType == ScaleSetupType.BtWifiR4
            && it.preferences?.shouldMeasureImpedance == true
            && !it.isWeighOnlyModeEnabledByOthers
        }

        if (r4Scales.isEmpty()) {
          AppLog.d("MetricInfoViewModel", "No R4 scales found")
          return@launch
        }

        // Separate scales by heart rate status
        val scalesWithHeartRateOn = r4Scales.filter {
          it.preferences?.shouldMeasurePulse == true
        }

        val scalesWithHeartRateOff = r4Scales.filter {
          it.preferences?.shouldMeasurePulse == false
        }

        AppLog.d(
          "MetricInfoViewModel",
          "R4 scales - Heart rate ON: ${scalesWithHeartRateOn.size}, Heart rate OFF: ${scalesWithHeartRateOff.size}"
        )

        when {
          // If multiple scales have heart rate OFF, navigate to My Scales screen
          scalesWithHeartRateOff.size > 1 -> {
            AppLog.d("MetricInfoViewModel", "Navigating to My Scales screen (multiple scales with heart rate OFF)")
            navigationService.navigateTo(AppRoute.AccountSettings.AddEditScales)
          }

          // If only one scale has heart rate OFF, navigate to that scale
          scalesWithHeartRateOff.size == 1 -> {
            val scaleId = scalesWithHeartRateOff.first().id
            AppLog.d("MetricInfoViewModel", "Navigating to scale with heart rate OFF: $scaleId")
            navigationService.navigateTo(AppRoute.ScaleDetails.ScaleMode(scaleId))
          }

          // All scales have heart rate ON (edge case)
          else -> {
            AppLog.d("MetricInfoViewModel", "All R4 scales have heart rate ON, navigating to first scale")
            val scaleId = r4Scales.first().id
            navigationService.navigateTo(AppRoute.ScaleDetails.ScaleMode(scaleId))
          }
        }
      } catch (e: Exception) {
        AppLog.e("MetricInfoViewModel", "Failed to navigate to scale mode", e.toString())
      }
    }
  }

  /**
   * Loads the dashboard type from the active account and selects the initial segment.
   */
  private fun loadDashboardTypeAndSelectSegment() {
    viewModelScope.launch {
      try {
        val activeAccount = accountRepository.getActiveAccount().first()
        val dashboardType = activeAccount?.dashboardType?.let { type ->
          DashboardType.entries.find { it.value == type }
        } ?: DashboardType.DASHBOARD_12_METRICS

        AppLog.d("MetricInfoViewModel", "Loaded dashboard type: ${dashboardType.value}")
        handleIntent(MetricInfoIntent.SetDashboardType(dashboardType))

        // Now select the segment with the correct dashboard type
        handleIntent(MetricInfoIntent.SelectSegment(key))
      } catch (e: Exception) {
        AppLog.e("MetricInfoViewModel", "Failed to load dashboard type", e.toString())
        // Default to 12 metrics on error
        handleIntent(MetricInfoIntent.SetDashboardType(DashboardType.DASHBOARD_12_METRICS))
        handleIntent(MetricInfoIntent.SelectSegment(key))
      }
    }
  }
}
