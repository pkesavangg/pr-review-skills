package com.dmdbrands.gurus.weight.migration.helper

import com.dmdbrands.gurus.weight.data.storage.db.entity.account.AccountEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BodyScaleEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BodyScaleEntryMetricEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.EntryEntity
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntryWithMetrics
import com.dmdbrands.gurus.weight.migration.model.IonicAccount
import com.dmdbrands.gurus.weight.migration.model.IonicScale
import com.dmdbrands.gurus.weight.proto.ThemeMode
import com.dmdbrands.gurus.weight.proto.UserAccount
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.util.Date
import java.util.UUID
import android.database.Cursor
import android.util.Log

/**
 * Utility class for converting between Ionic data formats and Android entities.
 * Handles all data transformations and conversions for migration.
 */
object IonicDataConverter {

  private const val TAG = "IonicDataConverter"

  /**
   * Extension function to convert string to ThemeMode.
   */
  fun String.toThemeMode(): ThemeMode {
    return when (this) {
      "system" -> ThemeMode.SYSTEM
      "light" -> ThemeMode.LIGHT
      "dark" -> ThemeMode.DARK
      else -> ThemeMode.SYSTEM
    }
  }

  /**
   * Parses the Account JSON string using Gson into IonicAccount object.
   */
  fun parseAccountWithGson(jsonString: String): IonicAccount? {
    return try {
      Log.d(TAG, "🔄 Parsing account JSON with Gson...")
      Log.d(TAG, "JSON preview: ${jsonString.take(200)}...")

      val gson = Gson()
      val ionicAccount = gson.fromJson(jsonString, IonicAccount::class.java)
      Log.d(TAG, "✅ Successfully parsed IonicAccount with ID: ${ionicAccount?.id}")
      ionicAccount
    } catch (e: JsonSyntaxException) {
      Log.e(TAG, "❌ JSON syntax error when parsing account: ${e.message}")
      Log.e(TAG, "❌ JSON content around error: ${getJsonContextAroundError(jsonString, e.message ?: "")}")
      null
    } catch (e: Exception) {
      Log.e(TAG, "❌ Error parsing account with Gson: ${e.message}")
      null
    }
  }

  /**
   * Helper function to get context around JSON parsing errors.
   */
  private fun getJsonContextAroundError(jsonString: String, errorMessage: String): String {
    return try {
      // Extract position information from error message if available
      val positionRegex = "line (\\d+) column (\\d+)".toRegex()
      val match = positionRegex.find(errorMessage)

      if (match != null) {
        val line = match.groupValues[1].toInt()
        val column = match.groupValues[2].toInt()

        // Find approximate character position
        val lines = jsonString.split('\n')
        if (line <= lines.size) {
          val targetLine = lines[line - 1]
          val start = maxOf(0, column - 50)
          val end = minOf(targetLine.length, column + 50)
          return "Line $line: ${targetLine.substring(start, end)}"
        }
      }

      // Fallback: show first 300 characters
      jsonString.take(300)
    } catch (e: Exception) {
      "Unable to extract context: ${e.message}"
    }
  }

  /**
   * Converts IonicAccount (parsed with Gson) to AccountEntity.
   */
  fun convertIonicAccountToAccountEntity(ionicAccount: IonicAccount): AccountEntity? {
    return try {
      Log.d(TAG, "🔄 Converting IonicAccount to AccountEntity...")

      val id = ionicAccount.id ?: UUID.randomUUID().toString()
      val firstName = ionicAccount.firstName ?: ""
      val lastName = ionicAccount.lastName ?: ""
      val email = ionicAccount.email ?: ""
      val gender = ionicAccount.gender ?: "male"
      val dob = ionicAccount.dob ?: ""
      val zipcode = ionicAccount.zipcode ?: ""
      val expiresAt = ionicAccount.expiresAt ?: ""

      // Validation
      if (email.isEmpty() || firstName.isEmpty()) {
        Log.w(TAG, "Missing required fields: email or firstName")
        return null
      }

      val accountEntity = AccountEntity(
        id = id,
        firstName = firstName,
        lastName = lastName,
        dob = dob,
        email = email,
        expiresAt = expiresAt,
        fcmToken = null, // Will be set by the app later
        gender = gender,
        isActiveAccount = true, // Since this was the active account
        isLoggedIn = true,
        isExpired = false,
        isSynced = true, // Will be synced later
        lastActiveTime = System.currentTimeMillis().toString(),
        zipcode = zipcode,
      )

      Log.i(TAG, "✅ IonicAccount converted successfully: $email")
      accountEntity
    } catch (e: Exception) {
      Log.e(TAG, "❌ Error converting IonicAccount to AccountEntity: ${e.message}")
      null
    }
  }

  /**
   * Converts IonicAccount to UserAccount.
   */
  fun convertIonicAccountToUserAccount(ionicAccount: IonicAccount): UserAccount? {
    val accessToken = ionicAccount.accessToken ?: ""
    val refreshToken = ionicAccount.refreshToken?.replace("\\u003d", "=") ?: ""
    val expiresAt = ionicAccount.expiresAt ?: ""

    val userAccount = UserAccount.newBuilder()
      .setAccessToken(accessToken)
      .setRefreshToken(refreshToken)
      .setExpiresAt(expiresAt)
      .build()

    return userAccount
  }

  /**
   * Parses devices JSON string that can be either an array of scales,
   * a wrapper object with devices array, or a map of devices.
   * Handles various JSON formats from the Ionic app data.
   */
  fun parseDevicesWithGson(devicesJsonString: String): List<IonicScale> {
    return try {
      Log.d(TAG, "🔄 Parsing devices JSON with Gson...")
      Log.d(TAG, "JSON preview: ${devicesJsonString.take(200)}...")
      val gson = Gson()
      val scaleArray = gson.fromJson(devicesJsonString, Array<IonicScale>::class.java)
      scaleArray.toList()
    } catch (e: Exception) {
      Log.e(TAG, "❌ Error parsing devices with Gson: ${e.message}")
      Log.e(TAG, "❌ JSON content: ${devicesJsonString.take(500)}")
      emptyList()
    }
  }

  /**
   * Legacy function for backward compatibility with Map<String, String> input.
   */
  fun parseDevicesWithGson(devicesJsonMap: Map<String, String>): Map<String, List<IonicScale>> {
    return try {
      Gson()
      devicesJsonMap.mapValues { (_, jsonString) ->
        // Use the main parsing function for each JSON string
        parseDevicesWithGson(jsonString)
      }
    } catch (e: Exception) {
      Log.e(TAG, "❌ Error parsing devices map with Gson: ${e.message}")
      emptyMap()
    }
  }

  /**
   * Converts a cursor row to ScaleEntry object.
   * This method handles both opStack and regular entries from the Ionic database.
   * It detects the cursor structure by checking if attempts column exists.
   */
  fun convertCursorToScaleEntry(cursor: Cursor, isOpStack: Boolean = false): ScaleEntry? {
    return try {
      val userId = cursor.getString(1) ?: return null
      val timestampStr = cursor.getString(2) ?: return null
      val operationType = cursor.getString(3) ?: "create"
      val weight = cursor.getInt(4)

      // Basic validation
      if (userId.isBlank() || weight <= 0) {
        return null
      }

      // Parse timestamp (keep as string for EntryEntity)
      val timestampString = parseIonicTimestamp(timestampStr)

      val entryEntity = buildIonicEntryEntity(cursor, userId, timestampString, operationType, isOpStack)
      val scaleEntryEntity = buildIonicScaleEntryEntity(cursor, weight)
      val scaleEntryMetricEntity = buildIonicScaleMetricEntity(cursor)

      // Get unit from metrics if available (may be null for opStack entries)
      val unit = parseIonicUnit(cursor)

      // Update entry entity with correct unit
      val updatedEntryEntity = entryEntity.copy(unit = unit)

      // Create ScaleEntryWithMetrics
      val scaleWithMetrics = ScaleEntryWithMetrics(
        scaleEntry = scaleEntryEntity,
        scaleEntryMetric = scaleEntryMetricEntity,
      )

      // Create the final ScaleEntry
      ScaleEntry(
        entry = updatedEntryEntity,
        scale = scaleWithMetrics,
      )
    } catch (e: Exception) {
      Log.w(TAG, "Error converting cursor to Entry ScaleEntry: ${e.message}")
      null
    }
  }

  /** Parses an Ionic timestamp column: epoch millis → Date string, otherwise used as-is. */
  private fun parseIonicTimestamp(timestampStr: String): String =
    try {
      when {
        timestampStr.matches(Regex("\\d+")) -> {
          // Convert epoch timestamp to ISO string
          val date = Date(timestampStr.toLong())
          date.toString()
        }

        else -> timestampStr // Use as-is
      }
    } catch (e: Exception) {
      timestampStr // Use original string as fallback
    }

  /** Resolves the weight unit from the cursor, defaulting to pounds. */
  private fun parseIonicUnit(cursor: Cursor): WeightUnit =
    try {
      val unitStr = cursor.getString(20)
      when (unitStr?.lowercase()) {
        "kg" -> WeightUnit.KG
        "lb", "lbs" -> WeightUnit.LB
        else -> WeightUnit.LB // Default to pounds
      }
    } catch (e: Exception) {
      WeightUnit.LB
    }

  /** Builds the migrated [EntryEntity] (unit is corrected by the caller via [parseIonicUnit]). */
  private fun buildIonicEntryEntity(
    cursor: Cursor,
    userId: String,
    timestampString: String,
    operationType: String,
    isOpStack: Boolean,
  ): EntryEntity =
    EntryEntity(
      id = 0, // Will be auto-generated
      accountId = userId,
      entryTimestamp = timestampString,
      serverTimestamp = timestampString, // Use same timestamp as fallback
      opTimestamp = timestampString,
      operationType = operationType,
      deviceType = "scale",
      deviceId = UUID.randomUUID().toString(),
      unit = WeightUnit.LB, // Default to pounds
      attempts = cursor.getIntOrNull(20) ?: 0,
      isSynced = !isOpStack,
    )

  /** Builds the migrated [BodyScaleEntryEntity] core body-composition values. */
  private fun buildIonicScaleEntryEntity(cursor: Cursor, weight: Int): BodyScaleEntryEntity =
    BodyScaleEntryEntity(
      id = 0, // Will be auto-generated
      weight = weight.toDouble(),
      bodyFat = cursor.getIntOrNull(5)?.toDouble(),
      muscleMass = cursor.getIntOrNull(6)?.toDouble(),
      water = cursor.getIntOrNull(7)?.toDouble(),
      bmi = cursor.getIntOrNull(8)?.toDouble(),
      source = cursor.getStringOrNull(9) ?: "IONIC_MIGRATION",
    )

  /** Builds the migrated [BodyScaleEntryMetricEntity] advanced-metric values. */
  private fun buildIonicScaleMetricEntity(cursor: Cursor): BodyScaleEntryMetricEntity =
    BodyScaleEntryMetricEntity(
      id = 0, // Will be auto-generated
      bmr = cursor.getIntOrNull(11)?.toDouble(),
      metabolicAge = cursor.getIntOrNull(12),
      proteinPercent = cursor.getIntOrNull(13)?.toDouble(),
      pulse = cursor.getIntOrNull(14),
      skeletalMusclePercent = cursor.getIntOrNull(15)?.toDouble(),
      subcutaneousFatPercent = cursor.getIntOrNull(16)?.toDouble(),
      visceralFatLevel = cursor.getIntOrNull(17)?.toDouble(),
      boneMass = cursor.getIntOrNull(18)?.toDouble(),
      impedance = cursor.getIntOrNull(19), // Available in entry_metric, null in opStack_metric
    )

  /**
   * Helper extension to safely get nullable integers from cursor.
   */
  private fun Cursor.getIntOrNull(columnIndex: Int): Int? {
    return if (isNull(columnIndex)) null else getInt(columnIndex)
  }

  /**
   * Helper extension to safely get nullable strings from cursor.
   */
  private fun Cursor.getStringOrNull(columnIndex: Int): String? {
    return if (isNull(columnIndex)) null else getString(columnIndex)
  }
}
