package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.enums.MetricKey
import com.dmdbrands.gurus.weight.domain.enums.MetricKeyConstants
import com.dmdbrands.gurus.weight.domain.enums.MilestoneKey
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.domain.repository.IDashboardRepository
import com.dmdbrands.gurus.weight.domain.services.IDashboardService
import com.dmdbrands.gurus.weight.features.common.model.DashboardKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of IDashboardService for dashboard visible metrics and milestones management.
 */
@Singleton
class DashboardService
@Inject
constructor(
  private val dashboardRepository: IDashboardRepository,
  private val accountRepository: IAccountRepository
) : IDashboardService {
  private var accountId: String? = null
  private var repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private val _selectedKey = MutableStateFlow<DashboardKey?>(null)
  override val selectedKey: StateFlow<DashboardKey?>
    get() = _selectedKey.asStateFlow()

  private val _visibleKeys = MutableStateFlow<List<DashboardKey>>(listOf())
  override val visibleKeys: StateFlow<List<DashboardKey>>
    get() = _visibleKeys.asStateFlow()

  /**
   * Sets the current account ID to be used by default in other methods.
   */
  override suspend fun setAccountId(accountId: String) {
    clearAllData()
    this.accountId = accountId
    refreshDashboard(accountId)
    repositoryScope.launch {
      getVisibleKeys(accountId).collect {
        _visibleKeys.value = it
      }
    }
  }

  override suspend fun setSelectedKey(key: DashboardKey?) {
    _selectedKey.value = key
  }

  private fun clearAllData() {
    repositoryScope.cancel()
    accountId = null
    _visibleKeys.value = emptyList()
    repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  }

  override suspend fun refreshDashboard(accountId: String?) {
    try {
      AppLog.d("DashboardService", "Refreshing dashboard data from server for account: $accountId")
      val accountId = accountId ?: this.accountId ?: throw IllegalStateException("Account ID must be set")
      val account = accountRepository.getAccountFromAPI(accountId)
      val dashboardType = DashboardType.entries.find { it.value == account.dashboardType }
        ?: DashboardType.DASHBOARD_4_METRICS

      // Get dashboard metrics from server (already in camelCase format)
      val serverMetrics = account.dashboardMetrics ?: emptyList()

      AppLog.d("DashboardService", "Server dashboardType: ${dashboardType.value}")
      AppLog.d("DashboardService", "Server dashboardMetrics: $serverMetrics")

      // Update local database with server data
      serverMetrics.joinToString(",")

      // Get current milestones from database, or use defaults if not set
      val currentMilestones = try {
        dashboardRepository.getVisibleMilestoneKeys(accountId).first()
      } catch (e: Exception) {
        emptyList<MilestoneKey>()
      }

      AppLog.d(
        "DashboardService",
        "Using milestones: ${if (currentMilestones.isNotEmpty()) currentMilestones else MilestoneKey.getDefaultMilestones()}",
      )

      accountRepository.updateDashboardSettings(
        accountId = accountId,
        dashboardMetrics = serverMetrics,
        dashboardMilestones = if (currentMilestones.isNotEmpty()) {
          currentMilestones.map { it.name.lowercase() }
        } else {
          // Use default milestones if none are set
          MilestoneKey.getDefaultMilestones().map { it.name.lowercase() }
        },
        dashboardType = dashboardType,
      )

      AppLog.d("DashboardService", "Dashboard data refreshed from server successfully")
    } catch (e: Exception) {
      AppLog.e("DashboardService", "Failed to refresh dashboard from server", e.toString())
    }
  }

  /**
   * Gets a Flow of visible metric keys for the given account.
   * If accountId is null, uses the stored accountId.
   */
  override fun getVisibleMetricKeys(accountId: String?): Flow<List<MetricKey>> =
    dashboardRepository.getVisibleMetricKeys(
      accountId ?: this.accountId ?: throw IllegalStateException("Account ID must be set"),
    )

  /**
   * Gets a Flow of visible milestone keys for the given account.
   * If accountId is null, uses the stored accountId.
   */
  override fun getVisibleMilestoneKeys(accountId: String?): Flow<List<MilestoneKey>> =
    dashboardRepository.getVisibleMilestoneKeys(
      accountId ?: this.accountId ?: throw IllegalStateException("Account ID must be set"),
    )

  override fun getVisibleKeys(accountId: String?): Flow<List<DashboardKey>> =
    combine(
      dashboardRepository.getVisibleMetricKeys(
        accountId ?: this.accountId ?: throw IllegalStateException("Account ID must be set"),
      ),
      dashboardRepository.getVisibleMilestoneKeys(
        accountId ?: this.accountId ?: throw IllegalStateException("Account ID must be set"),
      ),
    ) { metrics, milestones ->
      val metricsKey = metrics.map { DashboardKey.Metric(it) }
      val milestonesKey = milestones.map { DashboardKey.Milestone(it) }
      listOf(metricsKey, milestonesKey).flatten()
    }

  /**
   * Updates the visible metric keys for the given account.
   * If accountId is null, uses the stored accountId.
   */
  override suspend fun updateVisibleMetricKeys(
    accountId: String?,
    keys: List<MetricKey>,
    dashboardType: DashboardType,
  ) {
    val id = accountId ?: this.accountId ?: throw IllegalStateException("Account ID must be set")
    dashboardRepository.updateVisibleMetricKeys(id, keys, dashboardType)

    // Refresh the visible keys StateFlow to notify subscribers
    refreshVisibleKeysFromDatabase(id)
  }

  /**
   * Updates the visible milestone keys for the given account.
   * If accountId is null, uses the stored accountId.
   */
  override suspend fun updateVisibleMilestoneKeys(
    accountId: String?,
    keys: List<MilestoneKey>,
  ) = dashboardRepository.updateVisibleMilestoneKeys(
    accountId ?: this.accountId ?: throw IllegalStateException("Account ID must be set"),
    keys,
  )

  override suspend fun updateVisibleKeys(accountId: String?, keys: List<DashboardKey>, dashboardType: DashboardType) {
    try {
      val id = accountId ?: this.accountId ?: throw IllegalStateException("Account ID must be set")

      AppLog.d("DashboardService", "Updating visible keys with dashboardType: ${dashboardType.value}")

      val metrics = keys.filterIsInstance<DashboardKey.Metric>().map { it.key }
      val milestones = keys.filterIsInstance<DashboardKey.Milestone>().map { it.key }

      // Update both metrics and milestones together to avoid dashboard type conflicts
      accountRepository.updateDashboardSettings(
        accountId = id,
        dashboardMetrics = metrics.map { MetricKeyConstants.ENUM_TO_CAMEL_CASE[it] ?: it.name.lowercase() },
        dashboardMilestones = milestones.map { it.name.lowercase() },
        dashboardType = dashboardType,
      )

      // Update both dashboard metrics and dashboard type via API
      val dashboardKeys = metrics.map { MetricKeyConstants.ENUM_TO_CAMEL_CASE[it] ?: it.name.lowercase() }

      AppLog.d("DashboardService", "Sending to server - dashboardKeys: $dashboardKeys")
      AppLog.d("DashboardService", "Sending to server - dashboardType: ${dashboardType.value}")

      accountRepository.updateDashboardMetrics(dashboardKeys)

      // Refresh the visible keys StateFlow to notify subscribers
      refreshVisibleKeysFromDatabase(id)
    } catch (e: Exception) {
      AppLog.e("DashboardService", "Failed to update visible keys", e.toString())
    }
  }

  /**
   * Checks if the given accountId has a visible keys entry.
   * If accountId is null, uses the stored accountId.
   */
  override suspend fun hasVisibleKeys(accountId: String?): Boolean =
    dashboardRepository.hasVisibleKeys(
      accountId ?: this.accountId ?: throw IllegalStateException("Account ID must be set"),
    )

  /**
   * Resets the visible metric keys for the given account to the default list.
   * If accountId is null, uses the stored accountId.
   */
  override suspend fun resetVisibleMetricKeys(accountId: String?, dashboardType: DashboardType) =
    dashboardRepository.resetVisibleMetricKeys(
      accountId ?: this.accountId ?: throw IllegalStateException("Account ID must be set"),
      dashboardType,
    )

  /**
   * Resets the visible milestone keys for the given account to the default list.
   * If accountId is null, uses the stored accountId.
   */
  override suspend fun resetVisibleMilestoneKeys(accountId: String?) =
    dashboardRepository.resetVisibleMilestoneKeys(
      accountId ?: this.accountId ?: throw IllegalStateException("Account ID must be set"),
    )

  /**
   * Resets both visible metric and milestone keys for the given account to the default lists.
   * If accountId is null, uses the stored accountId.
   */
  override suspend fun resetVisibleKeys(accountId: String?, dashboardType: DashboardType) =
    dashboardRepository.resetVisibleKeys(
      accountId ?: this.accountId ?: throw IllegalStateException("Account ID must be set"),
      dashboardType,
    )

  /**
   * Refreshes the visible keys StateFlow from the database to notify subscribers of changes.
   * This should be called after updating visible keys to ensure the UI reflects the changes immediately.
   */
  private suspend fun refreshVisibleKeysFromDatabase(accountId: String) {
    try {
      val updatedKeys = getVisibleKeys(accountId).first()
      _visibleKeys.value = updatedKeys
      AppLog.d("DashboardService", "Refreshed visible keys StateFlow: $updatedKeys")
    } catch (e: Exception) {
      AppLog.e("DashboardService", "Failed to refresh visible keys from database", e.toString())
    }
  }
}
