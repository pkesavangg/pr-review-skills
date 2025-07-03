package com.greatergoods.meapp.data.storage.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.greatergoods.meapp.proto.DashboardKey
import com.greatergoods.meapp.proto.VisibleKeys
import com.greatergoods.meapp.proto.VisibleMetrics
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
 * DataStore for managing visible metrics per account.
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
    suspend fun getVisibleKeys(accountId: String): List<DashboardKey> =
        getData().accountMetricMap[accountId]?.visibleKeysList ?: emptyList()

    /**
     * Updates the visible keys for a specific account.
     *
     * - If the account does not exist and [keys] is empty, sets the default keys.
     * - If the account exists and [keys] is empty, sets the list to empty.
     * - Otherwise, sets the list to the provided [keys].
     *
     * @param accountId The account ID.
     * @param keys The list of DashboardKey to set.
     */
    suspend fun updateVisibleKeys(accountId: String, keys: List<DashboardKey> = listOf()) {
        updateData { current ->
            val accountExists = hasVisibleKeys(accountId)
            val toSet = when {
                !accountExists && keys.isEmpty() -> defaultVisibleKeys()
                else -> keys
            }
            current.toBuilder()
                .putAccountMetricMap(
                    accountId,
                    VisibleKeys.newBuilder().addAllVisibleKeys(toSet).build(),
                )
                .build()
        }
    }

    /**
     * Returns the default list of visible metric keys (all except METRIC_KEY_UNSPECIFIED).
     */
    private fun defaultVisibleKeys(): List<DashboardKey> =
        DashboardKey.entries.filter { it != DashboardKey.DASHBOARD_KEY_UNSPECIFIED }

    /**
     * Clears all visible metrics data (removes all accounts).
     */
    override suspend fun clearData() {
        updateData { VisibleMetrics.getDefaultInstance() }
    }

    /**
     * Checks if the given accountId has a visible keys entry.
     * @param accountId The account ID to check.
     * @return True if the accountId is present, false otherwise.
     */
    suspend fun hasVisibleKeys(accountId: String): Boolean =
        getData().accountMetricMap.containsKey(accountId)

    /**
     * Resets the visible keys for the given account to the default list.
     * @param accountId The account ID.
     */
    suspend fun resetVisibleKeys(accountId: String) {
        updateData { current ->
            current.toBuilder()
                .putAccountMetricMap(
                    accountId,
                    VisibleKeys.newBuilder().addAllVisibleKeys(defaultVisibleKeys()).build(),
                )
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
