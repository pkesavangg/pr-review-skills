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
 * DataStore for managing goal alert and goal card popup state per account.
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
   * Gets the goal card status value for a specific account.
   * This method mimics the Angular kvStorage.getValue() functionality.
   * @param accountId The account ID to check
   * @return The stored value as a string ("true" if shown, null if not set)
   */
  suspend fun getGoalCardValue(accountId: String): String? {
    return try {
      val hasShown = getData().accountGoalCardStatusMap.getOrDefault(accountId, false)
      if (hasShown) "true" else null
    } catch (e: Exception) {
      AppLog.e("GoalAlertDataStore", "Error getting goal card value for account $accountId", e.toString())
      null
    }
  }

  /**
   * Sets the goal card status value for a specific account.
   * This method mimics the Angular kvStorage.setValue() functionality.
   * @param accountId The account ID to update
   * @param value The value to set (typically "true")
   */
  suspend fun setGoalCardValue(accountId: String, value: String) {
    try {
      val hasShown = value == "true"
      updateData { currentData ->
        currentData.toBuilder()
          .putAccountGoalCardStatus(accountId, hasShown)
          .build()
      }
      AppLog.d("GoalAlertDataStore", "Goal card value set for account $accountId: $value")
    } catch (e: Exception) {
      AppLog.e("GoalAlertDataStore", "Error setting goal card value for account $accountId", e.toString())
    }
  }

  /**
   * Resets all alert and goal card states (for all accounts).
   */
  override suspend fun clearData() {
    super.clearData()
    AppLog.d("GoalAlertDataStore", "All goal alert and goal card states cleared")
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


