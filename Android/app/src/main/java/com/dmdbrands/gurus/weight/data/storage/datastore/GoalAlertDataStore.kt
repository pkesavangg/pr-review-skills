package com.dmdbrands.gurus.weight.data.storage.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.proto.GoalAlertProto
import java.io.InputStream
import java.io.OutputStream
import android.content.Context

private val Context.goalAlertDataStore: DataStore<GoalAlertProto> by dataStore(
  fileName = "goal_alert.pb",
  serializer = GoalAlertProtoSerializer,
)

/**
 * DataStore for managing goal alert state per account.
 */
class GoalAlertDataStore(
  context: Context
) : BaseProtoDataStore<GoalAlertProto>(context.goalAlertDataStore) {

  override fun getDefaultInstance(): GoalAlertProto = GoalAlertProto.getDefaultInstance()

  /**
   * Checks if the alert has been shown for a specific account.
   * @param accountId The account ID to check
   * @return True if alert has been shown for this account, false otherwise
   */
  suspend fun hasShownAlert(accountId: String): Boolean {
    return getData().accountAlertsMap.getOrDefault(accountId, false)
  }

  /**
   * Sets whether the goal alert has been shown for a specific account.
   * @param accountId The account ID to update
   * @param hasShown Whether the alert has been shown
   */
  suspend fun setAlertShown(accountId: String, hasShown: Boolean) {
    try {
      updateData { currentData ->
        currentData.toBuilder()
          .putAccountAlerts(accountId, hasShown)
          .build()
      }
    } catch (e: Exception) {
      AppLog.e("GoalAlertDataStore", "Error updating alert shown state for account $accountId", e.toString())
    }
  }

  /**
   * Resets the alert state for a specific account.
   * @param accountId The account ID to reset
   */
  suspend fun resetAlertState(accountId: String) {
    try {
      updateData { currentData ->
        currentData.toBuilder()
          .removeAccountAlerts(accountId)
          .build()
      }
    } catch (e: Exception) {
      AppLog.e("GoalAlertDataStore", "Error resetting alert state for account $accountId", e.toString())
    }
  }

  /**
   * Resets all alert states (for all accounts).
   */
  override suspend fun clearData() {
    super.clearData()
  }
}

private object GoalAlertProtoSerializer : Serializer<GoalAlertProto> {
  override val defaultValue: GoalAlertProto = GoalAlertProto.getDefaultInstance()

  override suspend fun readFrom(input: InputStream): GoalAlertProto {
    return try {
      GoalAlertProto.parseFrom(input)
    } catch (exception: Exception) {
      AppLog.e("GoalAlertProtoSerializer", "Error reading proto", exception.toString())
      defaultValue
    }
  }

  override suspend fun writeTo(t: GoalAlertProto, output: OutputStream) = t.writeTo(output)
}


