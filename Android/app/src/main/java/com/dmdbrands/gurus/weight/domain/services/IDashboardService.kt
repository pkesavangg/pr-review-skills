package com.dmdbrands.gurus.weight.domain.services

import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.enums.MetricKey
import com.dmdbrands.gurus.weight.domain.enums.MilestoneKey
import com.dmdbrands.gurus.weight.features.common.model.DashboardKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Service interface for dashboard visible metrics and milestones management.
 */
interface IDashboardService {

  val visibleKeys: StateFlow<List<DashboardKey>>
  val selectedKey: StateFlow<DashboardKey?>

  /**
   * Sets the current account ID to be used by default in other methods.
   * @param accountId The account ID to set as default.
   */
  suspend fun setAccountId(accountId: String)

  suspend fun refreshDashboard(accountId: String? = null)

  suspend fun setSelectedKey(key: DashboardKey?)

  /**
   * Gets a Flow of visible metric keys for the given account.
   * If accountId is null, uses the stored accountId.
   * @param accountId The account ID (optional).
   * @return Flow emitting the list of visible metric keys.
   */
  fun getVisibleMetricKeys(accountId: String? = null): Flow<List<MetricKey>>

  /**
   * Gets a Flow of visible milestone keys for the given account.
   * If accountId is null, uses the stored accountId.
   * @param accountId The account ID (optional).
   * @return Flow emitting the list of visible milestone keys.
   */
  fun getVisibleMilestoneKeys(accountId: String? = null): Flow<List<MilestoneKey>>

  fun getVisibleKeys(accountId: String? = null): Flow<List<DashboardKey>>

  /**
   * Updates the visible metric keys for the given account.
   * If accountId is null, uses the stored accountId.
   * @param accountId The account ID (optional).
   * @param keys The list of MetricKey to set.
   * @param dashboardType The dashboard type to determine default keys.
   */
  suspend fun updateVisibleMetricKeys(
    accountId: String? = null,
    keys: List<MetricKey> = listOf(),
    dashboardType: DashboardType = DashboardType.DASHBOARD_4_METRICS
  )

  /**
   * Updates the visible milestone keys for the given account.
   * If accountId is null, uses the stored accountId.
   * @param accountId The account ID (optional).
   * @param keys The list of MilestoneKey to set.
   */
  suspend fun updateVisibleMilestoneKeys(accountId: String? = null, keys: List<MilestoneKey> = listOf())

  suspend fun updateVisibleKeys(
    accountId: String? = null,
    keys: List<DashboardKey> = listOf(),
    dashboardType: DashboardType = DashboardType.DASHBOARD_4_METRICS
  )

  /**
   * Checks if the given accountId has a visible keys entry.
   * If accountId is null, uses the stored accountId.
   * @param accountId The account ID to check (optional).
   * @return True if the accountId is present, false otherwise.
   */
  suspend fun hasVisibleKeys(accountId: String? = null): Boolean

  /**
   * Resets the visible metric keys for the given account to the default list.
   * If accountId is null, uses the stored accountId.
   * @param accountId The account ID (optional).
   * @param dashboardType The dashboard type to determine default keys.
   */
  suspend fun resetVisibleMetricKeys(
    accountId: String? = null,
    dashboardType: DashboardType = DashboardType.DASHBOARD_4_METRICS
  )

  /**
   * Resets the visible milestone keys for the given account to the default list.
   * If accountId is null, uses the stored accountId.
   * @param accountId The account ID (optional).
   */
  suspend fun resetVisibleMilestoneKeys(accountId: String? = null)

  /**
   * Resets both visible metric and milestone keys for the given account to the default lists.
   * If accountId is null, uses the stored accountId.
   * @param accountId The account ID (optional).
   * @param dashboardType The dashboard type to determine default metric keys.
   */
  suspend fun resetVisibleKeys(
    accountId: String? = null,
    dashboardType: DashboardType = DashboardType.DASHBOARD_4_METRICS
  )
}
