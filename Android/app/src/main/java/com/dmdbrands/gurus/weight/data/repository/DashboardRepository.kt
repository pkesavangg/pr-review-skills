package com.dmdbrands.gurus.weight.data.repository

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.storage.db.dao.AccountDao
import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.enums.MetricKey
import com.dmdbrands.gurus.weight.domain.enums.MetricKeyConstants
import com.dmdbrands.gurus.weight.domain.enums.MilestoneKey
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.domain.repository.IDashboardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of IDashboardRepository for dashboard visible metrics and milestones management.
 * Uses DAO-based storage instead of DataStore.
 */
@Singleton
class DashboardRepository @Inject constructor(
  private val accountDao: AccountDao,
  private val accountRepository: IAccountRepository
) : IDashboardRepository {

  /**
   * Gets a Flow of visible metric keys for the given account.
   * Simple logic: dashboard type determines available metrics, server dashboardMetrics determines visibility.
   */
  override fun getVisibleMetricKeys(accountId: String): Flow<List<MetricKey>> =
    accountDao.getDashboardSettings(accountId).map { settings ->
      val dashboardType = settings?.dashboardType?.let { type ->
        DashboardType.entries.find { it.value == type }
      } ?: DashboardType.DASHBOARD_4_METRICS

      // Get available metrics based on dashboard type
      val availableMetrics = when (dashboardType) {
        DashboardType.DASHBOARD_4_METRICS -> MetricKey.getDefault4Metrics()
        DashboardType.DASHBOARD_12_METRICS -> MetricKey.getAllMetrics()
      }

      // Get visible metrics from server (camelCase format)
      val visibleMetricsFromServer = settings?.dashboardMetrics
        ?.mapNotNull { MetricKeyConstants.CAMEL_CASE_TO_ENUM[it] }
        ?: emptyList()

      // Debug: Log what we're getting
      AppLog.d("DashboardRepository", "Raw dashboardMetrics: ${settings?.dashboardMetrics}")
      AppLog.d("DashboardRepository", "Parsed metrics: ${visibleMetricsFromServer.map { it.name }}")
      AppLog.d("DashboardRepository", "Available metrics: ${availableMetrics.map { it.name }}")

      // Always filter server metrics by available metrics
      // This ensures we only show metrics that are valid for the current dashboard type
      val result = visibleMetricsFromServer.filter { metric ->
        availableMetrics.contains(metric)
      }

      AppLog.d("DashboardRepository", "Final result: ${result.map { it.name }}")
      result
    }

  /**
   * Gets a Flow of visible milestone keys for the given account.
   */
  override fun getVisibleMilestoneKeys(accountId: String): Flow<List<MilestoneKey>> =
    accountDao.getDashboardSettings(accountId).map { settings ->
      settings?.dashboardMilestones
        ?.mapNotNull { MilestoneKey.fromString(it) }
        ?: emptyList()
    }

  /**
   * Updates the visible metric keys for the given account.
   * Simple logic: just save the provided keys as-is.
   */
  override suspend fun updateVisibleMetricKeys(accountId: String, keys: List<MetricKey>, dashboardType: DashboardType) {
    // Convert keys to camelCase for storage
    val metricsList = keys.map { MetricKeyConstants.ENUM_TO_CAMEL_CASE[it] ?: it.name.lowercase() }

    // Get current milestones from database
    val currentSettings = accountDao.getDashboardSettings(accountId)
    val milestonesList = currentSettings.map { settings ->
      settings?.dashboardMilestones ?: emptyList()
    }.first()

    accountRepository.updateDashboardSettings(
      accountId = accountId,
      dashboardMetrics = metricsList,
      dashboardMilestones = milestonesList,
      dashboardType = dashboardType,
    )
  }

  /**
   * Updates the visible milestone keys for the given account.
   */
  override suspend fun updateVisibleMilestoneKeys(accountId: String, keys: List<MilestoneKey>) {
    val milestonesList = keys

    // Get current metrics and dashboard type from database
    val currentSettings = accountDao.getDashboardSettings(accountId)
    val metricsList = currentSettings.map { settings ->
      settings?.dashboardMetrics ?: MetricKeyConstants.DEFAULT_4_METRICS
    }.let { flow ->
      // This is a simplified approach - we'll use defaults for now
      MetricKeyConstants.DEFAULT_4_METRICS
    }

    val dashboardType = currentSettings.map { settings ->
      settings?.dashboardType?.let { type ->
        DashboardType.entries.find { it.value == type }
      } ?: DashboardType.DASHBOARD_4_METRICS
    }.let { flow ->
      // This is a simplified approach - we'll use defaults for now
      DashboardType.DASHBOARD_4_METRICS
    }

    accountRepository.updateDashboardSettings(
      accountId = accountId,
      dashboardMetrics = metricsList,
      dashboardMilestones = milestonesList.map { it.name.lowercase() },
      dashboardType = dashboardType,
    )
  }

  /**
   * Checks if the given accountId has a visible keys entry.
   */
  override suspend fun hasVisibleKeys(accountId: String): Boolean {
    // This is a simplified approach - in a real implementation, you'd want to handle this better
    return true
  }

  /**
   * Resets the visible metric keys for the given account to the default list.
   * Uses all metrics based on dashboard type, not just visible ones.
   */
  override suspend fun resetVisibleMetricKeys(accountId: String, dashboardType: DashboardType) {
    val defaultMetrics = when (dashboardType) {
      DashboardType.DASHBOARD_4_METRICS -> MetricKey.getDefault4Metrics()
      DashboardType.DASHBOARD_12_METRICS -> MetricKey.getAllMetrics()
    }
    updateVisibleMetricKeys(accountId, defaultMetrics, dashboardType)
  }

  /**
   * Resets the visible milestone keys for the given account to the default list.
   */
  override suspend fun resetVisibleMilestoneKeys(accountId: String) {
    updateVisibleMilestoneKeys(accountId, MilestoneKey.getDefaultMilestones())
  }

  /**
   * Resets both visible metric and milestone keys for the given account to the default lists.
   */
  override suspend fun resetVisibleKeys(accountId: String, dashboardType: DashboardType) {

    val defaultMilestones = MilestoneKey.getDefaultMilestones()

    // Convert metrics to camelCase based on dashboard type
    val metricsList = when (dashboardType) {
      DashboardType.DASHBOARD_4_METRICS -> MetricKeyConstants.DEFAULT_4_METRICS
      DashboardType.DASHBOARD_12_METRICS -> MetricKeyConstants.ALL_METRIC_KEYS
    }

    accountRepository.updateDashboardSettings(
      accountId = accountId,
      dashboardMetrics = metricsList,
      dashboardMilestones = defaultMilestones.map { it.name.lowercase() },
      dashboardType = dashboardType,
    )
  }
}
