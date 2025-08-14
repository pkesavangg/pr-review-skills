package com.dmdbrands.gurus.weight.migration

import com.dmdbrands.gurus.weight.data.storage.db.entity.account.AccountEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BodyScaleEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BodyScaleEntryMetricEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.EntryEntity
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntryWithMetrics
import com.dmdbrands.gurus.weight.proto.MetricKey
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
   * Extension function to convert string to MetricKey.
   */
  fun String.toMetricKey(): MetricKey? {
    return when (this) {
      "bmi" -> MetricKey.BMI
      "bodyFat" -> MetricKey.BODY_FAT
      "muscleMass" -> MetricKey.MUSCLE_MASS
      "water" -> MetricKey.BODY_WATER
      "boneMass" -> MetricKey.BONE_MASS
      "visceralFatLevel" -> MetricKey.VISCERAL_FAT
      "subcutaneousFatPercent" -> MetricKey.SUBCUTANEOUS_FAT
      "proteinPercent" -> MetricKey.PROTEIN
      "skeletalMusclePercent" -> MetricKey.SKELETAL_MUSCLE
      "pulse" -> MetricKey.HEART_RATE
      "bmr" -> MetricKey.BMR
      "metabolicAge" -> MetricKey.METABOLIC_AGE
      else -> null
    }
  }

  /**
   * Parses the Account JSON string using Gson into IonicAccount object.
   */
  fun parseAccountWithGson(jsonString: String): IonicAccount? {
    return try {
      Log.d(TAG, "🔄 Parsing account JSON with Gson...")
      val gson = Gson()
      val ionicAccount = gson.fromJson(jsonString, IonicAccount::class.java)
      Log.d(TAG, "✅ Successfully parsed IonicAccount with ID: ${ionicAccount.id}")
      ionicAccount
    } catch (e: JsonSyntaxException) {
      Log.e(TAG, "❌ JSON syntax error when parsing account: ${e.message}")
      null
    } catch (e: Exception) {
      Log.e(TAG, "❌ Error parsing account with Gson: ${e.message}")
      null
    }
  }

  /**
   * Converts IonicAccount (parsed with Gson) to AccountEntity.
   */
  fun convertIonicAccountToAccountEntity(ionicAccount: IonicAccount): AccountEntity? {
    return try {
      Log.d(TAG, "🔄 Converting IonicAccount to AccountEntity...")

      val id = ionicAccount.id ?: UUID.randomUUID().toString()
      val profile = ionicAccount.profile
      val firstName = profile?.firstName ?: ""
      val lastName = profile?.lastName ?: ""
      val email = profile?.email ?: ""
      val gender = profile?.gender?.name?.lowercase() ?: "male"
      val dob = profile?.dob ?: ""
      val zipcode = profile?.zipcode ?: ""
      val tokens = ionicAccount.tokens
      val expiresAt = tokens?.expiresAt ?: ""
      val isLoggedIn = (ionicAccount.loggedIn ?: 0) == 1

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
        isLoggedIn = isLoggedIn,
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
    val tokens = ionicAccount.tokens
    val accessToken = tokens?.accessToken?.replace("\\u003d", "=") ?: ""
    val refreshToken = tokens?.refreshToken?.replace("\\u003d", "=") ?: ""
    val expiresAt = tokens?.expiresAt ?: ""

    val userAccount = UserAccount.newBuilder()
      .setAccessToken(accessToken)
      .setRefreshToken(refreshToken)
      .setExpiresAt(expiresAt)
      .build()

    return userAccount
  }

  /**
   * Converts a cursor row to ScaleEntry object.
   */
  fun convertCursorToScaleEntry(cursor: Cursor): ScaleEntry? {
    return try {
      val userId = cursor.getString(1) ?: return null
      val timestampStr = cursor.getString(2) ?: return null
      val weight = cursor.getInt(4)

      // Basic validation
      if (userId.isBlank() || weight <= 0) {
        return null
      }

      // Parse timestamp (keep as string for EntryEntity)
      val timestampString = try {
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

      // Create EntryEntity
      val entryEntity = EntryEntity(
        id = 0, // Will be auto-generated
        accountId = userId,
        entryTimestamp = timestampString,
        serverTimestamp = timestampString,
        opTimestamp = timestampString,
        operationType = "CREATE",
        deviceType = "scale",
        deviceId = UUID.randomUUID().toString(),
        unit = WeightUnit.LB, // Default to pounds
        isSynced = false,
      )

      // Create BodyScaleEntryEntity
      val scaleEntryEntity = BodyScaleEntryEntity(
        id = 0, // Will be auto-generated
        weight = weight.toDouble(),
        bodyFat = cursor.getIntOrNull(5)?.toDouble(),
        muscleMass = cursor.getIntOrNull(6)?.toDouble(),
        water = cursor.getIntOrNull(7)?.toDouble(),
        bmi = cursor.getIntOrNull(8)?.toDouble(),
        source = cursor.getStringOrNull(9) ?: "IONIC_MIGRATION",
      )

      // Create BodyScaleEntryMetricEntity
      val scaleEntryMetricEntity = BodyScaleEntryMetricEntity(
        id = 0, // Will be auto-generated
        bmr = cursor.getIntOrNull(11)?.toDouble(),
        metabolicAge = cursor.getIntOrNull(12),
        proteinPercent = cursor.getIntOrNull(13)?.toDouble(),
        pulse = cursor.getIntOrNull(14),
        skeletalMusclePercent = cursor.getIntOrNull(15)?.toDouble(),
        subcutaneousFatPercent = cursor.getIntOrNull(16)?.toDouble(),
        visceralFatLevel = cursor.getIntOrNull(17)?.toDouble(),
        boneMass = cursor.getIntOrNull(18)?.toDouble(),
        impedance = null, // Not available in Ionic data
      )

      // Create ScaleEntryWithMetrics
      val scaleWithMetrics = ScaleEntryWithMetrics(
        scaleEntry = scaleEntryEntity,
        scaleEntryMetric = scaleEntryMetricEntity,
      )

      // Create the final ScaleEntry
      ScaleEntry(
        entry = entryEntity,
        scale = scaleWithMetrics,
      )
    } catch (e: Exception) {
      Log.w(TAG, "Error converting cursor to ScaleEntry: ${e.message}")
      null
    }
  }

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
