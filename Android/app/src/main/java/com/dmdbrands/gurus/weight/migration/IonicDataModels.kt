package com.dmdbrands.gurus.weight.migration

import com.google.gson.annotations.SerializedName

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
 * Clean Account data class using proper composition with domain models.
 * Uses clean architecture principles with separated concerns.
 */
data class IonicAccount(
  // Account specific fields
  @SerializedName("id") val id: String? = null,
  @SerializedName("loggedIn") val loggedIn: Int? = null,
  @SerializedName("dashboardType") val dashboardType: DashboardType? = null,

  // Composed domain objects
  @SerializedName("goal") val goal: Goal? = null,
  @SerializedName("profile") val profile: Profile? = null,
  @SerializedName("weightless") val weightless: Weightless? = null,
  @SerializedName("streakStatus") val streakStatus: StreakStatus? = null,
  @SerializedName("notifications") val notifications: Notifications? = null,
  @SerializedName("integrations") val integrations: Integrations? = null,
  @SerializedName("tokens") val tokens: Tokens? = null,
  @SerializedName("dashboardMetrics") val dashboardMetrics: DashboardMetrics? = null
)

/**
 * Database analysis result.
 */
data class DatabaseAnalysis(
  val totalEntries: Int,
  val totalMetrics: Int,
  val hasData: Boolean
)

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
