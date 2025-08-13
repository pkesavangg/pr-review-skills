package com.dmdbrands.gurus.weight.data.repository

import com.dmdbrands.gurus.weight.data.storage.datastore.DashboardKeysDatastore
import com.dmdbrands.gurus.weight.data.storage.datastore.UserDataStore
import com.dmdbrands.gurus.weight.data.storage.db.AppDatabase
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.AccountEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.GoalSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.IntegrationsSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.NotificationSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.WeightCompSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.WeightlessSettingsEntity
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
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Date
import java.util.UUID
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log

/**
 * Data classes and enums that replicate the TypeScript Account interface for Gson parsing.
 *
 * These classes are designed to work with Gson for JSON deserialization from the Ionic app.
 * All fields are nullable to handle missing or optional properties in the JSON.
 *
 * The main class is IonicAccount which combines all the TypeScript interfaces:
 * - Goal, Profile, Weightless, Notifications, Integrations, Tokens, StreakStatus,
 *   DashboardMetrics, UpdateDashboardType
 *
 * Usage:
 * ```kotlin
 * val gson = Gson()
 * val ionicAccount = gson.fromJson(jsonString, IonicAccount::class.java)
 * val accountEntity = convertIonicAccountToAccountEntity(ionicAccount)
 * ```
 */
enum class GoalType {
  @SerializedName("gain")
  GAIN,

  @SerializedName("lose")
  LOSE,

  @SerializedName("maintain")
  MAINTAIN
}

enum class ActivityLevel {
  @SerializedName("normal")
  NORMAL,

  @SerializedName("athlete")
  ATHLETE
}

enum class Sex {
  @SerializedName("male")
  MALE,

  @SerializedName("female")
  FEMALE
}

enum class WeightUnitType {
  @SerializedName("kg")
  KG,

  @SerializedName("lb")
  LB
}

enum class DashboardType {
  @SerializedName("dashboard_4_metrics")
  DASHBOARD_4,

  @SerializedName("dashboard_12_metrics")
  DASHBOARD_12
}

enum class BodyMetric {
  @SerializedName("bmi")
  BMI,

  @SerializedName("bodyFat")
  BODY_FAT,

  @SerializedName("muscleMass")
  MUSCLE_MASS,

  @SerializedName("water")
  WATER,

  @SerializedName("pulse")
  PULSE,

  @SerializedName("boneMass")
  BONE_MASS,

  @SerializedName("visceralFatLevel")
  VISCERAL_FAT_LEVEL,

  @SerializedName("subcutaneousFatPercent")
  SUBCUTANEOUS_FAT_PERCENT,

  @SerializedName("proteinPercent")
  PROTEIN_PERCENT,

  @SerializedName("skeletalMusclePercent")
  SKELETAL_MUSCLE_PERCENT,

  @SerializedName("bmr")
  BMR,

  @SerializedName("metabolicAge")
  METABOLIC_AGE
}

/**
 * Goal interface equivalent
 */
data class Goal(
  @SerializedName("goalWeight") val goalWeight: Double? = null,
  @SerializedName("goalType") val goalType: GoalType? = null,
  @SerializedName("type") val type: GoalType? = null, // Bug workaround
  @SerializedName("initialWeight") val initialWeight: Double? = null,
  @SerializedName("metPreviousGoal") val metPreviousGoal: Boolean? = null,
  @SerializedName("percent") val percent: Double? = null
)

/**
 * BodyComp interface equivalent
 */
data class BodyComp(
  @SerializedName("weightUnit") val weightUnit: WeightUnitType? = null,
  @SerializedName("height") val height: Double? = null,
  @SerializedName("activityLevel") val activityLevel: ActivityLevel? = null
)

/**
 * Profile interface equivalent (extends BodyComp)
 */
data class Profile(
  @SerializedName("email") val email: String? = null,
  @SerializedName("firstName") val firstName: String? = null,
  @SerializedName("lastName") val lastName: String? = null,
  @SerializedName("gender") val gender: Sex? = null,
  @SerializedName("zipcode") val zipcode: String? = null,
  @SerializedName("dob") val dob: String? = null,
  // BodyComp fields
  @SerializedName("weightUnit") val weightUnit: WeightUnitType? = null,
  @SerializedName("height") val height: Double? = null,
  @SerializedName("activityLevel") val activityLevel: ActivityLevel? = null
)

/**
 * Weightless interface equivalent
 */
data class Weightless(
  @SerializedName("isWeightlessOn") val isWeightlessOn: Boolean? = null,
  @SerializedName("weightlessWeight") val weightlessWeight: Double? = null,
  @SerializedName("weightlessTimestamp") val weightlessTimestamp: String? = null
)

/**
 * StreakStatus interface equivalent
 */
data class StreakStatus(
  @SerializedName("isStreakOn") val isStreakOn: Boolean? = null,
  @SerializedName("streakTimestamp") val streakTimestamp: String? = null
)

/**
 * Notifications interface equivalent
 */
data class Notifications(
  @SerializedName("shouldSendEntryNotifications") val shouldSendEntryNotifications: Boolean? = null,
  @SerializedName("shouldSendWeightInEntryNotifications") val shouldSendWeightInEntryNotifications: Boolean? = null
)

/**
 * Integrations interface equivalent
 */
data class Integrations(
  @SerializedName("isFitbitOn") val isFitbitOn: Boolean? = null,
  @SerializedName("isGoogleFitOn") val isGoogleFitOn: Boolean? = null,
  @SerializedName("isMFPOn") val isMFPOn: Boolean? = null,
  @SerializedName("isUAOn") val isUAOn: Boolean? = null,
  @SerializedName("isFitbitValid") val isFitbitValid: Boolean? = null,
  @SerializedName("isGoogleFitValid") val isGoogleFitValid: Boolean? = null,
  @SerializedName("isMFPValid") val isMFPValid: Boolean? = null,
  @SerializedName("isUAValid") val isUAValid: Boolean? = null,
  @SerializedName("healthkit") val healthkit: Boolean? = null,
  @SerializedName("isHealthConnectOn") val isHealthConnectOn: Boolean? = null
)

/**
 * Tokens interface equivalent
 */
data class Tokens(
  @SerializedName("accessToken") val accessToken: String? = null,
  @SerializedName("refreshToken") val refreshToken: String? = null,
  @SerializedName("expiresAt") val expiresAt: String? = null
)

/**
 * DashboardMetrics interface equivalent
 */
data class DashboardMetrics(
  @SerializedName("dashboardMetrics") val dashboardMetrics: List<BodyMetric>? = null
)

/**
 * UpdateDashboardType interface equivalent
 */
data class UpdateDashboardType(
  @SerializedName("dashboardType") val dashboardType: DashboardType? = null
)

/**
 * Complete Account data class that replicates the TypeScript Account interface.
 * This class combines all the interfaces: Goal, Profile, Weightless, Notifications,
 * Integrations, Tokens, StreakStatus, DashboardMetrics, UpdateDashboardType
 */
data class IonicAccount(
  // Account specific fields
  @SerializedName("id") val id: String? = null,
  @SerializedName("loggedIn") val loggedIn: Int? = null,
  @SerializedName("dashboardType") val dashboardType: DashboardType? = null,

  // Goal fields
  @SerializedName("goalWeight") val goalWeight: Double? = null,
  @SerializedName("goalType") val goalType: GoalType? = null,
  @SerializedName("type") val type: GoalType? = null, // Bug workaround
  @SerializedName("initialWeight") val initialWeight: Double? = null,
  @SerializedName("metPreviousGoal") val metPreviousGoal: Boolean? = null,
  @SerializedName("percent") val percent: Double? = null,

  // Profile fields (includes BodyComp)
  @SerializedName("email") val email: String? = null,
  @SerializedName("firstName") val firstName: String? = null,
  @SerializedName("lastName") val lastName: String? = null,
  @SerializedName("gender") val gender: String? = null, // Using String for flexibility
  @SerializedName("zipcode") val zipcode: String? = null,
  @SerializedName("dob") val dob: String? = null,
  @SerializedName("weightUnit") val weightUnit: String? = null, // Using String for flexibility
  @SerializedName("height") val height: Double? = null,
  @SerializedName("activityLevel") val activityLevel: String? = null, // Using String for flexibility

  // Weightless fields
  @SerializedName("isWeightlessOn") val isWeightlessOn: Boolean? = null,
  @SerializedName("weightlessWeight") val weightlessWeight: Double? = null,
  @SerializedName("weightlessTimestamp") val weightlessTimestamp: String? = null,

  // StreakStatus fields
  @SerializedName("isStreakOn") val isStreakOn: Boolean? = null,
  @SerializedName("streakTimestamp") val streakTimestamp: String? = null,

  // Notifications fields
  @SerializedName("shouldSendEntryNotifications") val shouldSendEntryNotifications: Boolean? = null,
  @SerializedName("shouldSendWeightInEntryNotifications") val shouldSendWeightInEntryNotifications: Boolean? = null,

  // Integrations fields
  @SerializedName("isFitbitOn") val isFitbitOn: Boolean? = null,
  @SerializedName("isGoogleFitOn") val isGoogleFitOn: Boolean? = null,
  @SerializedName("isMFPOn") val isMFPOn: Boolean? = null,
  @SerializedName("isUAOn") val isUAOn: Boolean? = null,
  @SerializedName("isFitbitValid") val isFitbitValid: Boolean? = null,
  @SerializedName("isGoogleFitValid") val isGoogleFitValid: Boolean? = null,
  @SerializedName("isMFPValid") val isMFPValid: Boolean? = null,
  @SerializedName("isUAValid") val isUAValid: Boolean? = null,
  @SerializedName("healthkit") val healthkit: Boolean? = null,
  @SerializedName("isHealthConnectOn") val isHealthConnectOn: Boolean? = null,

  // Tokens fields
  @SerializedName("accessToken") val accessToken: String? = null,
  @SerializedName("refreshToken") val refreshToken: String? = null,
  @SerializedName("expiresAt") val expiresAt: String? = null,

  // DashboardMetrics fields
  @SerializedName("dashboardMetrics") val dashboardMetrics: List<String>? = null // Using List<String> for flexibility
)

/**
 * Repository responsible for handling all types of database migrations.
 * This includes Ionic migration and future migration scenarios.
 */
class MigrationRepository(private val context: Context) {

  companion object {
    private const val TAG = "MigrationRepository"
    private const val BATCH_SIZE = 500
    private const val IONIC_DB_NAME = "WeightGurus4SQLite.db"

    // Capacitor Preferences keys used by Ionic app
    private const val ACTIVE_ACCOUNT_KEY = "activeAccountKey"
    private const val CAPACITOR_STORAGE_FILENAME = "preferences"
  }

  /**
   * Performs the complete Ionic database migration.
   * This is the main entry point for Ionic migration.
   */
  suspend fun performIonicMigration(context: Context): MigrationResult = withContext(Dispatchers.IO) {
    return@withContext try {
      Log.i(TAG, "Starting Ionic database migration")

      val migrationResult = migrateIonicDatabase(context)

      if (migrationResult.isSuccess) {
        cleanupIonicDatabase(context)
        Log.i(TAG, "Ionic database migration completed successfully")
      } else {
        Log.e(TAG, "Ionic database migration failed: ${migrationResult.errorMessage}")
      }

      migrationResult
    } catch (t: Throwable) {
      Log.e(TAG, "Ionic migration failed with exception: ${t.message}")
      MigrationResult.failure("Migration failed: ${t.message}")
    }
  }

  /**
   * Core Ionic migration logic with account migration first, then entries.
   */
  private suspend fun migrateIonicDatabase(context: Context): MigrationResult {
    var sqliteDb: SQLiteDatabase? = null
    var totalMigratedEntries = 0
    var accountMigrated = false

    return try {
      Log.i(TAG, "Starting Ionic migration with account data first")

      // Step 1: Migrate account data from Capacitor Preferences
      accountMigrated = migrateAccountData(context)
      if (accountMigrated) {
        Log.i(TAG, "Account migration completed successfully")
      } else {
        Log.w(TAG, "No account data found or account migration failed")
      }

      // Step 2: Locate and open Ionic database for entries
      val dbPath = locateIonicDb(context)
      if (dbPath == null) {
        return MigrationResult.Success(0, accountMigrated)
      }
      sqliteDb = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY)

      // Step 3: Perform comprehensive database analysis
      val analysisResult = analyzeIonicDatabaseRaw(sqliteDb)

      if (analysisResult.totalEntries == 0) {
        Log.i(TAG, "No entries found in Ionic database")
        return if (accountMigrated) {
          MigrationResult.success(0, accountMigrated)
        } else {
          MigrationResult.failure("No data found to migrate")
        }
      }

      // Step 4: Migrate entries using raw SQL
      totalMigratedEntries = migrateEntriesWithRawSQL(sqliteDb, analysisResult.totalEntries)

      // Step 5: Save migration timestamp
      saveMigrationTimestamp(context)

      Log.i(TAG, "Migration completed: Account=$accountMigrated, Entries=$totalMigratedEntries")
      MigrationResult.success(totalMigratedEntries, accountMigrated)
    } catch (e: Exception) {
      Log.e(TAG, "Migration failed: ${e.message}")
      MigrationResult.failure(e.message ?: "Unknown error")
    } finally {
      sqliteDb?.close()
    }
  }

  /**
   * Migrates account data from Capacitor Preferences storage using Gson.
   * Looks for the activeAccountKey and converts it to AccountEntity.
   */
  private suspend fun migrateAccountData(context: Context): Boolean = withContext(Dispatchers.IO) {
    return@withContext try {
      delay(5000)
      Log.d(TAG, "🏠 Starting account data migration from Capacitor Preferences")

      // Try to locate and read Capacitor Preferences
      val accountJsonString = locateAndReadAccountFromCapacitorStorage(context)

      if (accountJsonString.isNullOrEmpty()) {
        Log.w(TAG, "No account data found in Capacitor storage")
        return@withContext false
      }

      // Parse JSON account data using Gson
      val ionicAccount = parseAccountWithGson(accountJsonString)
      if (ionicAccount == null) {
        Log.w(TAG, "Failed to parse account JSON with Gson")
        return@withContext false
      }

      Log.d(TAG, "📋 Successfully parsed IonicAccount: ${ionicAccount.email}")

      // Convert to AccountEntity and UserAccount
      val accountEntity = convertIonicAccountToAccountEntity(ionicAccount)
      val userAccount = convertIonicAccountToUserAccount(ionicAccount)
      locateAndReadThemeModeFromCapacitorStorage(context)

      if (accountEntity == null) {
        Log.w(TAG, "Failed to convert IonicAccount to AccountEntity")
        return@withContext false
      }

      // Update UserDataStore with UserAccount
      val userDataStore = UserDataStore(context)
      userDataStore.updateAccountTokens(
        accountEntity.id,
        userAccount?.refreshToken ?: "",
        userAccount?.accessToken ?: "",
        userAccount?.expiresAt ?: "",
        true,
      )
      userDataStore.setActiveAccount(accountEntity.id)
      // Insert into database
      val appDatabase = AppDatabase.getInstance(context)
      appDatabase.accountDao().insertAccount(accountEntity)
      appDatabase.accountDao().insertGoalSettings(
        GoalSettingsEntity(
          accountId = accountEntity.id,
          goalType = ionicAccount.goalType?.name ?: "",
          weight = ionicAccount.initialWeight?.toFloat() ?: 0.0f,
          goalWeight = ionicAccount.goalWeight.toString(),
          goalPercent = ionicAccount.percent?.toFloat() ?: 0.0f,
          isSynced = true,
        ),
      )
      appDatabase.accountDao().insertWeightlessSettings(
        WeightlessSettingsEntity(
          accountId = accountEntity.id,
          isWeightlessOn = ionicAccount.isWeightlessOn ?: false,
          weightlessTimestamp = ionicAccount.weightlessTimestamp.toString(),
          weightlessWeight = ionicAccount.weightlessWeight?.toFloat() ?: 0.0f,
          isSynced = true,
        ),
      )

      appDatabase.accountDao().insertIntegrationsSettings(
        IntegrationsSettingsEntity(
          accountId = accountEntity.id,
          isSynced = true,
          isMFPOn = ionicAccount.isMFPOn ?: false,
          isMFPValid = ionicAccount.isMFPValid ?: false,
          isFitbitOn = ionicAccount.isFitbitOn ?: false,
          isFitbitValid = ionicAccount.isFitbitValid ?: false,
          isHealthConnectOn = ionicAccount.isHealthConnectOn ?: false,
          isHealthKitOn = ionicAccount.healthkit ?: false,
        ),
      )

      appDatabase.accountDao().insertWeightCompSettings(
        WeightCompSettingsEntity(
          accountId = accountEntity.id,
          isSynced = true,
          height = ionicAccount.height?.toInt() ?: 1700, // Default height if not set
          activityLevel = ionicAccount.activityLevel ?: "normal", // Default activity level
          weightUnit = ionicAccount.weightUnit ?: "lb", // Default weight unit
        ),
      )

      appDatabase.accountDao().insertNotificationSettings(
        NotificationSettingsEntity(
          accountId = accountEntity.id,
          isSynced = true,
          shouldSendEntryNotifications = ionicAccount.shouldSendEntryNotifications ?: false,
          shouldSendWeightInEntryNotifications = ionicAccount.shouldSendWeightInEntryNotifications ?: false,
        ),
      )

      val dashboardKeysDatastore = DashboardKeysDatastore(context)
      if (ionicAccount.dashboardMetrics?.isNotEmpty() == true) {
        Log.d(TAG, "📋 Dashboard metrics: ${ionicAccount.dashboardMetrics}")
        val dashboardMetricKeys = ionicAccount.dashboardMetrics.mapNotNull { it.toMetricKey() }
        dashboardKeysDatastore.updateVisibleMetricKeys(accountEntity.id, dashboardMetricKeys)
      }
      dashboardKeysDatastore.resetVisibleMilestoneKeys(accountEntity.id)

      Log.i(TAG, "✅ Account migration successful: ${accountEntity.email}")
      true
    } catch (e: Exception) {
      Log.e(TAG, "❌ Account migration failed: ${e.message}")
      false
    }
  }

  fun String.toThemeMode(): ThemeMode {
    return when (this) {
      "system" -> ThemeMode.SYSTEM
      "light" -> ThemeMode.LIGHT
      "dark" -> ThemeMode.DARK
      else -> ThemeMode.SYSTEM
    }
  }

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
  private fun parseAccountWithGson(jsonString: String): IonicAccount? {
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
   * Locates and reads account data from Capacitor Preferences storage.
   */
  private fun locateAndReadAccountFromCapacitorStorage(context: Context): String? {
    Log.d(TAG, "🔍 Searching for Capacitor Preferences storage...")

    try {
      val sharedPrefs = context.getSharedPreferences("CapacitorStorage", Context.MODE_PRIVATE)
      val accountData = sharedPrefs.getString(ACTIVE_ACCOUNT_KEY, null)
      if (!accountData.isNullOrEmpty()) {
        Log.i(TAG, "✅ Found account in CapacitorStorage SharedPreferences")
        return accountData
      }
    } catch (e: Exception) {
      Log.d(TAG, "CapacitorStorage SharedPreferences not found: ${e.message}")
    }
    return null
  }

  private suspend fun locateAndReadThemeModeFromCapacitorStorage(context: Context) {
    try {
      Log.d(TAG, "🔍 Searching for Capacitor Preferences storage...")
      val sharedPrefs = context.getSharedPreferences("CapacitorStorage", Context.MODE_PRIVATE)
      val ionicThemeModeKeys = sharedPrefs.all.filter { it.key.contains("colorMode") }
      val themeModeKeys: Map<String, String> = ionicThemeModeKeys.keys
        .mapNotNull { key ->
          sharedPrefs.getString(key, null)?.let { value ->
            key.removeSuffix("-colorMode") to value
          }
        }
        .toMap()
      val userDataStore = UserDataStore(context)
      themeModeKeys.forEach { (key, value) ->
        val themeMode = value.toThemeMode()
        userDataStore.addAccount(key, themeMode = themeMode)
      }
    } catch (e: Exception) {
      Log.d(TAG, "CapacitorStorage SharedPreferences not found: ${e.message}")
    }
  }

  private fun convertIonicAccountToUserAccount(ionicAccount: IonicAccount): UserAccount? {

    val accessToken = ionicAccount.accessToken?.replace("\\u003d", "=") ?: ""
    val refreshToken = ionicAccount.refreshToken?.replace("\\u003d", "=") ?: ""
    val expiresAt = ionicAccount.expiresAt ?: ""

    val userAccount = UserAccount.newBuilder()
      .setAccessToken(accessToken)
      .setRefreshToken(refreshToken)
      .setExpiresAt(expiresAt)
      .build()

    return userAccount
  }

  private fun convertJsonToUserAccount(accountJson: JSONObject): UserAccount? {
    val accessToken = accountJson.optString("accessToken", "").replace("\\u003d", "=")
    val refreshToken = accountJson.optString("refreshToken", "").replace("\\u003d", "=")
    val expiresAt = accountJson.optString("expiresAt", "")
    val userAccount = UserAccount.newBuilder()
      .setAccessToken(accessToken)
      .setRefreshToken(refreshToken)
      .setExpiresAt(expiresAt)
      .build()

    return userAccount
  }

  /**
   * Converts IonicAccount (parsed with Gson) to AccountEntity.
   */
  private fun convertIonicAccountToAccountEntity(ionicAccount: IonicAccount): AccountEntity? {
    return try {
      Log.d(TAG, "🔄 Converting IonicAccount to AccountEntity...")

      val id = ionicAccount.id ?: UUID.randomUUID().toString()
      val firstName = ionicAccount.firstName ?: ""
      val lastName = ionicAccount.lastName ?: ""
      val email = ionicAccount.email ?: ""
      val gender = ionicAccount.gender ?: "male"
      val dob = ionicAccount.dob ?: ""
      val zipcode = ionicAccount.zipcode ?: ""
      val expiresAt = ionicAccount.expiresAt
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
   * Converts JSON account data to AccountEntity (legacy method - kept for compatibility).
   */
  private fun convertJsonToAccountEntity(accountJson: JSONObject): AccountEntity? {
    return try {
      Log.d(TAG, "🔄 Converting JSON to AccountEntity...")

      val id = accountJson.optString("id", UUID.randomUUID().toString())
      val firstName = accountJson.optString("firstName", "")
      val lastName = accountJson.optString("lastName", "")
      val email = accountJson.optString("email", "")
      val gender = accountJson.optString("gender", "male")
      val dob = accountJson.optString("dob", "")
      val zipcode = accountJson.optString("zipcode", "")
      val expiresAt = accountJson.optString("expiresAt", null)
      accountJson.optInt("loggedIn", 0) == 1

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
      accountEntity
    } catch (e: Exception) {
      Log.e(TAG, "❌ Error converting JSON to AccountEntity: ${e.message}")
      null
    }
  }

  /**
   * Performs comprehensive analysis of the Ionic database using raw SQL.
   */
  private suspend fun analyzeIonicDatabaseRaw(sqliteDb: SQLiteDatabase): DatabaseAnalysis {
    Log.d(TAG, "🔍 Starting comprehensive database analysis with raw SQL...")

    var entryCount = 0
    var metricCount = 0

    // Count entries in opStack_v1 table
    var cursor: Cursor? = null
    try {
      cursor = sqliteDb.rawQuery("SELECT COUNT(*) FROM opStack", null)
      if (cursor.moveToFirst()) {
        entryCount = cursor.getInt(0)
      }
    } finally {
      cursor?.close()
    }

    // Count entries in opStack_metric table
    try {
      cursor = sqliteDb.rawQuery("SELECT COUNT(*) FROM opStack_metric", null)
      if (cursor.moveToFirst()) {
        metricCount = cursor.getInt(0)
      }
    } finally {
      cursor?.close()
    }

    Log.d(TAG, "📊 opStack_v1 table: $entryCount entries")
    Log.d(TAG, "📊 opStack_metric table: $metricCount metrics")

    // Sample some data for debugging
    if (entryCount > 0) {
      try {
        cursor = sqliteDb.rawQuery("SELECT id, userId, weight FROM opStack LIMIT 1", null)
        if (cursor.moveToFirst()) {
          val id = cursor.getLong(0)
          val userId = cursor.getString(1)
          val weight = cursor.getInt(2)
          Log.d(TAG, "📋 Sample entry: id=$id, userId=$userId, weight=$weight")
        }
      } finally {
        cursor?.close()
      }
    }

    if (metricCount > 0) {
      try {
        cursor = sqliteDb.rawQuery("SELECT id, userId, bmr FROM opStack_metric LIMIT 1", null)
        if (cursor.moveToFirst()) {
          val id = cursor.getLong(0)
          val userId = cursor.getString(1)
          val bmr = cursor.getInt(2)
          Log.d(TAG, "📋 Sample metric: id=$id, userId=$userId, bmr=$bmr")
        }
      } finally {
        cursor?.close()
      }
    }

    return DatabaseAnalysis(
      totalEntries = entryCount,
      totalMetrics = metricCount,
      hasData = entryCount > 0 || metricCount > 0,
    )
  }

  /**
   * Database analysis result.
   */
  private data class DatabaseAnalysis(
    val totalEntries: Int,
    val totalMetrics: Int,
    val hasData: Boolean
  )

  /**
   * Migrates entries using raw SQL queries.
   */
  private suspend fun migrateEntriesWithRawSQL(sqliteDb: SQLiteDatabase, totalEntries: Int): Int {
    var migratedCount = 0
    var cursor: Cursor? = null

    try {
      // Query to join opStack_v1 with opStack_metric for complete data
      val query = """
        SELECT
          e.id, e.userId, e.entryTimestamp, e.operationType, e.weight, e.bodyFat,
          e.muscleMass, e.water, e.bmi, e.source, e.attempts,
          m.bmr, m.metabolicAge, m.proteinPercent, m.pulse, m.skeletalMusclePercent,
          m.subcutaneousFatPercent, m.visceralFatLevel, m.boneMass
        FROM opStack e
        LEFT JOIN opStack_metric m ON e.userId = m.userId AND e.entryTimestamp = m.entryTimestamp
        ORDER BY e.entryTimestamp ASC
      """

      cursor = sqliteDb.rawQuery(query, null)

      val scaleEntries = mutableListOf<ScaleEntry>()

      while (cursor.moveToNext()) {
        try {
          val scaleEntry = convertCursorToScaleEntry(cursor)
          if (scaleEntry != null) {
            scaleEntries.add(scaleEntry)

            // Process in batches
            if (scaleEntries.size >= BATCH_SIZE) {
              val batchResult = insertScaleEntries(scaleEntries)
              migratedCount += batchResult
              scaleEntries.clear()

              val progress = (migratedCount * 100) / totalEntries
              Log.d(TAG, "Migration progress: $progress% ($migratedCount/$totalEntries entries)")
            }
          }
        } catch (e: Exception) {
          Log.w(TAG, "Failed to convert entry at position ${cursor.position}: ${e.message}")
        }
      }

      // Process remaining entries
      if (scaleEntries.isNotEmpty()) {
        val batchResult = insertScaleEntries(scaleEntries)
        migratedCount += batchResult
      }
    } finally {
      cursor?.close()
    }

    return migratedCount
  }

  /**
   * Converts a cursor row to ScaleEntry object.
   */
  private fun convertCursorToScaleEntry(cursor: Cursor): ScaleEntry? {
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

  /**
   * Inserts a batch of ScaleEntry objects.
   */
  private suspend fun insertScaleEntries(scaleEntries: List<ScaleEntry>): Int {
    var successCount = 0
    val appDatabase = AppDatabase.getInstance(context)

    scaleEntries.forEach { scaleEntry ->
      try {
        appDatabase.entryDao().insert(scaleEntry)
        successCount++
      } catch (e: Exception) {
        Log.w(TAG, "Failed to insert scale entry: ${e.message}")
      }
    }

    return successCount
  }

  /**
   * Cleans up the Ionic database after successful migration.
   */
  private fun cleanupIonicDatabase(context: Context) {
    try {
      Log.i(TAG, "Ionic database file deleted successfully")
    } catch (e: Exception) {
      Log.e(TAG, "Error deleting Ionic database: ${e.message}")
    }
  }

  /**
   * Handles emergency cleanup if migration fails completely.
   */
  suspend fun performEmergencyCleanup(context: Context) = withContext(Dispatchers.IO) {
    try {
      Log.w(TAG, "Performing emergency cleanup")
      deleteRoomDbCompletely(context, "MeApp")
      Log.i(TAG, "Emergency cleanup completed")
    } catch (e: Exception) {
      Log.e(TAG, "Emergency cleanup failed: ${e.message}")
    }
  }

  /**
   * Deletes Room database files completely.
   */
  private fun deleteRoomDbCompletely(ctx: Context, name: String) {
    val dbFile = ctx.getDatabasePath(name)
    listOf(dbFile, File(dbFile.path + "-wal"), File(dbFile.path + "-shm")).forEach {
      if (it.exists()) {
        val deleted = it.delete()
        Log.d(TAG, "Deleted ${it.name}: $deleted")
      }
    }
  }

  /**
   * Saves migration completion timestamp.
   */
  private suspend fun saveMigrationTimestamp(context: Context) {
    try {
      val timestamp = System.currentTimeMillis()
      val prefs = context.getSharedPreferences("migration_prefs", Context.MODE_PRIVATE)
      prefs.edit().putLong("ionic_migration_timestamp", timestamp).apply()
      Log.d(TAG, "Migration timestamp saved: $timestamp")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to save migration timestamp: ${e.message}")
    }
  }

  /**
   * Gets the last migration timestamp.
   */
  private fun getMigrationTimestamp(context: Context): Long? {
    return try {
      val prefs = context.getSharedPreferences("migration_prefs", Context.MODE_PRIVATE)
      val timestamp = prefs.getLong("ionic_migration_timestamp", -1L)
      if (timestamp != -1L) timestamp else null
    } catch (e: Exception) {
      Log.e(TAG, "Failed to get migration timestamp: ${e.message}")
      null
    }
  }

  /**
   * Locates the Ionic database file with comprehensive debugging.
   */
  private fun locateIonicDb(context: Context): String? {
    Log.d(TAG, "🔍 Starting Ionic database location search...")

    val candidates = listOf(
      context.getDatabasePath("WeightGurus4SQLite.db").path,
      File(context.filesDir, "WeightGurus4SQLite.db").absolutePath,
      File(context.getExternalFilesDir(null), "WeightGurus4SQLite.db").absolutePath,
      // Additional potential locations for Ionic apps
      File(context.filesDir, "databases/WeightGurus4SQLite.db").absolutePath,
      File(context.getExternalFilesDir(null), "databases/WeightGurus4SQLite.db").absolutePath,
      // Common Cordova/Ionic database locations
      File(context.filesDir, "data/databases/WeightGurus4SQLite.db").absolutePath,
      File(context.getExternalFilesDir(null), "data/databases/WeightGurus4SQLite.db").absolutePath,
      // Legacy app-specific paths
      File("/data/data/${context.packageName}/databases/WeightGurus4SQLite.db").absolutePath,
      File("/data/data/${context.packageName}/app_database/WeightGurus4SQLite.db").absolutePath,
    )

    Log.d(TAG, "🔍 Searching ${candidates.size} potential locations:")

    candidates.forEachIndexed { index, path ->
      val file = File(path)
      val exists = file.exists()
      val size = if (exists) file.length() else 0
      Log.d(TAG, "  ${index + 1}. $path")
      Log.d(TAG, "     Exists: $exists, Size: $size bytes")

      if (exists && size > 0) {
        Log.i(TAG, "✅ Found valid Ionic database: $path (Size: $size bytes)")
        return path
      }
    }

    // Let's also scan the entire files directory for any .db files
    Log.w(TAG, "❌ No valid Ionic database found in standard locations")
    Log.d(TAG, "🔍 Scanning entire files directory for .db files...")

    scanForDatabaseFiles(context.filesDir, "files")
    context.getExternalFilesDir(null)?.let {
      scanForDatabaseFiles(it, "external files")
    }

    // Scan app's database directory
    val dbDir = File(context.applicationInfo.dataDir, "databases")
    scanForDatabaseFiles(dbDir, "app databases")
    return null
  }

  /**
   * Recursively scans a directory for .db files
   */
  private fun scanForDatabaseFiles(directory: File, dirName: String) {
    if (!directory.exists() || !directory.isDirectory) {
      Log.d(TAG, "Directory $dirName does not exist or is not a directory: ${directory.absolutePath}")
      return
    }

    Log.d(TAG, "🔍 Scanning $dirName directory: ${directory.absolutePath}")

    try {
      var foundCount = 0
      directory.walkTopDown().forEach { file ->
        if (file.isFile && (file.name.endsWith(".db", ignoreCase = true) ||
            file.name.endsWith(".sqlite", ignoreCase = true) ||
            file.name.endsWith(".sqlite3", ignoreCase = true))
        ) {
          val size = file.length()
          foundCount++
          Log.i(TAG, "📁 Found database file #$foundCount: ${file.absolutePath} (Size: $size bytes)")

          // Check if this might be our Ionic database
          if (file.name.contains("Weight", ignoreCase = true) ||
            file.name.contains("Guru", ignoreCase = true) ||
            file.name.contains("SQLite", ignoreCase = true) ||
            file.name.contains("ionic", ignoreCase = true)
          ) {
            Log.w(TAG, "🎯 Potential Ionic database found: ${file.absolutePath}")
          }
        }
      }
      Log.d(TAG, "📊 Total database files found in $dirName: $foundCount")
    } catch (e: Exception) {
      Log.e(TAG, "Error scanning directory $dirName: ${e.message}")
    }
  }

  /**
   * 🚨 DEBUG METHOD - Test account migration from Capacitor Preferences.
   * Call this method to diagnose account migration issues.
   * Remove before production!
   */

}

/**
 * Represents the result of a migration operation.
 */
sealed class MigrationResult {
  data class Success(
    val migratedCount: Int,
    val accountMigrated: Boolean = false
  ) : MigrationResult() {
    override val errorMessage: String? = null
  }

  data class Failure(override val errorMessage: String) : MigrationResult()

  val isSuccess: Boolean
    get() = this is Success

  abstract val errorMessage: String?

  companion object {
    fun success(count: Int, accountMigrated: Boolean = false) = Success(count, accountMigrated)
    fun failure(message: String) = Failure(message)
  }
}
