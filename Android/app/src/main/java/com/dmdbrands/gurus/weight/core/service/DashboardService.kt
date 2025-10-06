package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.domain.repository.IDashboardRepository
import com.dmdbrands.gurus.weight.domain.services.IDashboardService
import com.dmdbrands.gurus.weight.features.common.helper.StatHelper.toMetricKey
import com.dmdbrands.gurus.weight.features.common.helper.StatHelper.toStringKey
import com.dmdbrands.gurus.weight.features.common.model.DashboardKey
import com.dmdbrands.gurus.weight.proto.MetricKey
import com.dmdbrands.gurus.weight.proto.MilestoneKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

  private fun clearAllData() {
    repositoryScope.cancel()
    accountId = null
    _visibleKeys.value = emptyList()
    repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  }

  override suspend fun refreshDashboard(accountId: String?) {
    try {
      val accountId = accountId ?: this.accountId ?: throw IllegalStateException("Account ID must be set")
      val account = accountRepository.getAccountFromAPI(accountId)
      val metricKeys = account.dashboardMetrics.mapNotNull { it.toMetricKey() }
      dashboardRepository.updateVisibleMetricKeys(accountId, metricKeys)
    } catch (e: Exception) {
      AppLog.e("DashboardService", "Failed to refresh dashboard", e.toString())
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
  ) = dashboardRepository.updateVisibleMetricKeys(
    accountId ?: this.accountId ?: throw IllegalStateException("Account ID must be set"),
    keys,
  )

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

  override suspend fun updateVisibleKeys(accountId: String?, keys: List<DashboardKey>) {
    try {
      val id = accountId ?: this.accountId ?: throw IllegalStateException("Account ID must be set")
      val metrics = keys.filterIsInstance<DashboardKey.Metric>().map { it.key }
      val milestones = keys.filterIsInstance<DashboardKey.Milestone>().map { it.key }
      updateVisibleMetricKeys(id, metrics)
      updateVisibleMilestoneKeys(id, milestones)
      val dashboardKeys = metrics.map { it.toStringKey() }
      accountRepository.updateDashboardMetrics(dashboardKeys)
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
  override suspend fun resetVisibleMetricKeys(accountId: String?) =
    dashboardRepository.resetVisibleMetricKeys(
      accountId ?: this.accountId ?: throw IllegalStateException("Account ID must be set"),
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
  override suspend fun resetVisibleKeys(accountId: String?) =
    dashboardRepository.resetVisibleKeys(
      accountId ?: this.accountId ?: throw IllegalStateException("Account ID must be set"),
    )
}
