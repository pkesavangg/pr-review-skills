package com.dmdbrands.gurus.weight.data.storage.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.proto.MetricKey
import com.dmdbrands.gurus.weight.proto.MilestoneKey
import com.dmdbrands.gurus.weight.proto.VisibleKeys
import com.dmdbrands.gurus.weight.proto.VisibleMetrics
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.InputStream
import java.io.OutputStream
import android.content.Context

/**
 * Extension property to provide VisibleMetrics DataStore instance from Context.
 */
val Context.dashboardKeysDataStore: DataStore<VisibleMetrics> by dataStore(
  fileName = "visible_metrics.pb",
  serializer = VisibleMetricsSerializer,
)

/**
 * DataStore for managing visible metrics and milestones per account.
 *
 * @constructor Creates a VisibleMetricsDataStore with the given context.
 * @param context The application context.
 */
class DashboardKeysDatastore(
  context: Context
) : BaseProtoDataStore<VisibleMetrics>(
  dataStore = context.dashboardKeysDataStore,
) {
  /**
   * Emits a Flow of the current map of visible keys per account.
   */
  val accountVisibleKeysFlow: Flow<Map<String, VisibleKeys>> = dataFlow.map { it.accountMetricMap }

  /**
   * Gets the current map of visible keys per account.
   */
  suspend fun getAccountVisibleKeysMap(): Map<String, VisibleKeys> = getData().accountMetricMap

  /**
   * Gets the list of visible metric keys for a specific account.
   * @param accountId The account ID.
   * @return The list of visible metric keys, or empty if not set.
   */
  suspend fun getVisibleMetricKeys(accountId: String): List<MetricKey> =
    getData().accountMetricMap[accountId]?.visibleMetricKeysList ?: emptyList()

  /**
   * Gets the list of visible milestone keys for a specific account.
   * @param accountId The account ID.
   * @return The list of visible milestone keys, or empty if not set.
   */
  suspend fun getVisibleMilestoneKeys(accountId: String): List<MilestoneKey> =
    getData().accountMetricMap[accountId]?.visibleMilestoneKeysList ?: emptyList()

  /**
   * Updates the visible metric keys for a specific account.
   *
   * - If the account does not exist and [keys] is empty, sets the default metric keys.
   * - If the account exists and [keys] is empty, sets the list to empty.
   * - Otherwise, sets the list to the provided [keys].
   *
   * @param accountId The account ID.
   * @param keys The list of MetricKey to set.
   * @param dashboardType The dashboard type to determine default keys.
   */
  suspend fun updateVisibleMetricKeys(accountId: String, keys: List<MetricKey> = listOf(), dashboardType: DashboardType = DashboardType.DASHBOARD_4_METRICS) {
    updateData { current ->
      val accountExists = hasVisibleKeys(accountId)
      val toSet = when {
        !accountExists && keys.isEmpty() -> defaultMetricKeys(dashboardType)
        else -> keys
      }
      val currentVisibleKeys = current.accountMetricMap[accountId] ?: VisibleKeys.getDefaultInstance()
      val updatedVisibleKeys = currentVisibleKeys.toBuilder()
        .clearVisibleMetricKeys()
        .addAllVisibleMetricKeys(toSet)
        .build()

      current.toBuilder()
        .putAccountMetricMap(accountId, updatedVisibleKeys)
        .build()
    }
  }

  suspend fun initializeDashboardKeys(accountId: String, dashboardType: DashboardType = DashboardType.DASHBOARD_4_METRICS) {
    updateData { current ->
      // If the account already has visible keys, do nothing
      if (hasVisibleKeys(accountId)) return@updateData current

      // Otherwise, initialize both metric and milestone keys
      val defaultMetrics = defaultMetricKeys(dashboardType)
      val defaultMilestones = defaultMilestoneKeys()

      val newVisibleKeys = VisibleKeys.newBuilder()
        .addAllVisibleMetricKeys(defaultMetrics)
        .addAllVisibleMilestoneKeys(defaultMilestones)
        .build()

      current.toBuilder()
        .putAccountMetricMap(accountId, newVisibleKeys)
        .build()
    }
  }

  /**
   * Updates the visible milestone keys for a specific account.
   *
   * - If the account does not exist and [keys] is empty, sets the default milestone keys.
   * - If the account exists and [keys] is empty, sets the list to empty.
   * - Otherwise, sets the list to the provided [keys].
   *
   * @param accountId The account ID.
   * @param keys The list of MilestoneKey to set.
   */
  suspend fun updateVisibleMilestoneKeys(accountId: String, keys: List<MilestoneKey> = listOf()) {
    updateData { current ->
      val accountExists = hasVisibleKeys(accountId)
      val toSet = when {
        !accountExists && keys.isEmpty() -> defaultMilestoneKeys()
        else -> keys
      }
      val currentVisibleKeys = current.accountMetricMap[accountId] ?: VisibleKeys.getDefaultInstance()
      val updatedVisibleKeys = currentVisibleKeys.toBuilder()
        .clearVisibleMilestoneKeys()
        .addAllVisibleMilestoneKeys(toSet)
        .build()

      current.toBuilder()
        .putAccountMetricMap(accountId, updatedVisibleKeys)
        .build()
    }
  }

  /**
   * Returns the default list of visible metric keys based on dashboard type.
   * @param dashboardType The dashboard type to determine which metrics to include.
   */
  private fun defaultMetricKeys(dashboardType: DashboardType): List<MetricKey> = when (dashboardType) {
    DashboardType.DASHBOARD_4_METRICS -> listOf(
      MetricKey.BMI,
      MetricKey.BODY_FAT,
      MetricKey.MUSCLE_MASS,
      MetricKey.BODY_WATER
    )
    DashboardType.DASHBOARD_12_METRICS -> MetricKey.entries.filter { 
      it != MetricKey.UNRECOGNIZED && it != MetricKey.WEIGHT 
    }
  }

  /**
   * Returns the default list of visible milestone keys (all milestone keys).
   */
  private fun defaultMilestoneKeys(): List<MilestoneKey> =
    MilestoneKey.entries.filter { it != MilestoneKey.UNRECOGNIZED }

  override fun getDefaultInstance(): VisibleMetrics = VisibleMetrics.getDefaultInstance()

  /**
   * Optional: Override clearData() only if you need custom clear logic
   * Otherwise, the base implementation will use getDefaultInstance()
   */
  override suspend fun clearData() {
    super.clearData() // Use the base implementation
  }

  /**
   * Checks if the given accountId has a visible keys entry.
   * @param accountId The account ID to check.
   * @return True if the accountId is present, false otherwise.
   */
  suspend fun hasVisibleKeys(accountId: String): Boolean =
    getData().accountMetricMap.containsKey(accountId)

  /**
   * Resets the visible metric keys for the given account to the default list.
   * @param accountId The account ID.
   * @param dashboardType The dashboard type to determine default keys.
   */
  suspend fun resetVisibleMetricKeys(accountId: String, dashboardType: DashboardType = DashboardType.DASHBOARD_4_METRICS) {
    updateVisibleMetricKeys(accountId, defaultMetricKeys(dashboardType), dashboardType)
  }

  /**
   * Resets the visible milestone keys for the given account to the default list.
   * @param accountId The account ID.
   */
  suspend fun resetVisibleMilestoneKeys(accountId: String) {
    updateVisibleMilestoneKeys(accountId, defaultMilestoneKeys())
  }

  /**
   * Resets both visible metric and milestone keys for the given account to the default lists.
   * @param accountId The account ID.
   * @param dashboardType The dashboard type to determine default metric keys.
   */
  suspend fun resetVisibleKeys(accountId: String, dashboardType: DashboardType = DashboardType.DASHBOARD_4_METRICS) {
    updateData { current ->
      val updatedVisibleKeys = VisibleKeys.newBuilder()
        .addAllVisibleMetricKeys(defaultMetricKeys(dashboardType))
        .addAllVisibleMilestoneKeys(defaultMilestoneKeys())
        .build()

      current.toBuilder()
        .putAccountMetricMap(accountId, updatedVisibleKeys)
        .build()
    }
  }
}

/**
 * Serializer for VisibleMetrics proto.
 */
object VisibleMetricsSerializer : Serializer<VisibleMetrics> {
  override val defaultValue: VisibleMetrics = VisibleMetrics.getDefaultInstance()

  override suspend fun readFrom(input: InputStream): VisibleMetrics =
    VisibleMetrics.parseFrom(input)

  override suspend fun writeTo(t: VisibleMetrics, output: OutputStream) =
    t.writeTo(output)
}
