package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.domain.repository.IDashboardRepository
import com.dmdbrands.gurus.weight.domain.services.IDashboardService
import com.dmdbrands.gurus.weight.features.common.model.DashboardKey
import com.dmdbrands.gurus.weight.proto.MetricKey
import com.dmdbrands.gurus.weight.proto.MilestoneKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
) : IDashboardService {
  private var accountId: String? = null

  /**
   * Sets the current account ID to be used by default in other methods.
   */
  override fun setAccountId(accountId: String) {
    this.accountId = accountId
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
    val id = accountId ?: this.accountId ?: throw IllegalStateException("Account ID must be set")
    val metrics = keys.filterIsInstance<DashboardKey.Metric>().map { it.key }
    val milestones = keys.filterIsInstance<DashboardKey.Milestone>().map { it.key }
    updateVisibleMetricKeys(id, metrics)
    updateVisibleMilestoneKeys(id, milestones)
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
