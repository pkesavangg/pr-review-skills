package com.dmdbrands.gurus.weight.domain.model.api.user

data class AccountResponse(
  val accessToken: String?,
  val refreshToken: String?,
  val expiresAt: String?,
  val account: AccountInfo
)

data class AccountInfo(
  val id: String,
  val email: String,
  val firstName: String,
  val lastName: String,
  // Phase 2 (MOB-377): gender is optional at signup, so the server may echo it back as null.
  val gender: String? = null,
  val zipcode: String,
  val weightUnit: String,
  val isWeightlessOn: Boolean,
  // Nullable for baby-only accounts (Me App 2.0 spec §1.1/§1.4) — avoids login NPE (MOB-591).
  val height: Int?,
  // Nullable for baby-only accounts (Me App 2.0 spec §1.1/§1.4) — the server can omit
  // activityLevel just like height/dob, so a non-null field would re-trigger the login NPE
  // this PR fixes (MOB-591). Defaults applied at the mapping boundaries.
  val activityLevel: String?,
  val dob: String?,
  val weightlessTimestamp: String?,
  val weightlessWeight: Float?,
  val isStreakOn: Boolean,
  val dashboardType: String,
  val dashboardMetrics: List<String>,
  val progressMetrics: List<String>? = null,
  val goalType: String? = null,
  val goalWeight: Float?,
  val initialWeight: Float?,
  val metPreviousGoal: Boolean = false,      // whether previous goal was met
  val goalPercent: Int = 0,
  val shouldSendEntryNotifications: Boolean,
  val shouldSendWeightInEntryNotifications: Boolean,
  val isFitbitOn: Boolean = false,
  val isFitbitValid: Boolean = false,
  val isHealthConnectOn: Boolean = false,
  val isHealthKitOn: Boolean = false,
  val isMFPOn: Boolean = false,
  val isMFPValid: Boolean = false,
  // Phase 2 (MOB-377): account-level product list + measurement system. Nullable for
  // backward compatibility with pre-Phase-2 responses (default applied on mapping).
  val productTypes: List<String>? = null,
  val measurementUnits: String? = null,
)
