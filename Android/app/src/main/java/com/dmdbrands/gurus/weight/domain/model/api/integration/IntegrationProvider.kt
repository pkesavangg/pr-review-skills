package com.dmdbrands.gurus.weight.domain.model.api.integration

/**
 * Sealed class representing different integration providers.
 * This provides type safety while allowing for easy extensibility and API compatibility.
 */
sealed class IntegrationProvider(
  val apiValue: String,
  val displayName: String,
) {
  object Fitbit : IntegrationProvider("fitbit", "Fitbit")

  object MyFitnessPal : IntegrationProvider("mfPal", "MyFitnessPal")

  object HealthConnect : IntegrationProvider("healthConnect", "Health Connect")

  /**
   * Gets the OAuth authorization URL for this provider.
   * Based on actual implementation from wgApp4 config.ts
   * Returns null for providers that don't use OAuth (like Health Connect).
   */
  fun getOAuthUrl(accountId: String): String? =
    when (this) {
      is Fitbit -> "https://www.fitbit.com/oauth2/authorize?response_type=code&client_id=23TH5Z&redirect_uri=https%3A%2F%2Fapi.weightgurus.com%2Fv2%2Fauth%2Ffitbit&scope=profile%20weight&state=v3-$accountId"
      is MyFitnessPal -> "https://api.myfitnesspal.com/v2/oauth2/auth?client_id=weightguru&scope=measurements&response_type=code&redirect_uri=https%3a%2f%2fapi.weightgurus.com%2fv2%2fauth%2fmyfitnesspal?&state=v3-$accountId"
      is HealthConnect -> null // Health Connect doesn't use OAuth
    }

  /**
   * Checks if this provider requires OAuth flow.
   */
  fun requiresOAuth(): Boolean =
    when (this) {
      is Fitbit, is MyFitnessPal -> true
      is HealthConnect -> false // Health Connect uses platform-specific APIs
    }

  /**
   * Checks if this provider is platform-specific.
   */
  fun isPlatformSpecific(): Boolean =
    when (this) {
      is HealthConnect -> true
      is Fitbit, is MyFitnessPal -> false
    }

  /**
   * Gets the platform requirement for this provider.
   */
  fun getPlatformRequirement(): String? =
    when (this) {
      is HealthConnect -> "Android 13+"
      is Fitbit, is MyFitnessPal -> null
    }

  /**
   * Groups the provider into the section it belongs to on the Integrations screen.
   * Fitbit / MyFitnessPal sync weight only → [IntegrationCategory.WeightScale].
   * Health Connect syncs metrics relevant to paired devices → [IntegrationCategory.WeightScaleAndBpm].
   */
  fun getCategory(): IntegrationCategory =
    when (this) {
      is Fitbit, is MyFitnessPal -> IntegrationCategory.WeightScale
      is HealthConnect -> IntegrationCategory.WeightScaleAndBpm
    }

  /**
   * Gets the API endpoint for this provider.
   */
  fun getApiEndpoint(): String =
    when (this) {
      is Fitbit -> "fitbit"
      is MyFitnessPal -> "mfp"
      is HealthConnect -> "healthconnect"
    }

  /**
   * Gets the integration flag key for this provider.
   */
  fun getIntegrationFlagKey(): String =
    when (this) {
      is Fitbit -> "isFitbitOn"
      is MyFitnessPal -> "isMFPOn"
      is HealthConnect -> "isHealthConnectOn"
    }

  /**
   * Gets the integration validity flag key for this provider.
   */
  fun getValidityFlagKey(): String =
    when (this) {
      is Fitbit -> "isFitbitValid"
      is MyFitnessPal -> "isMFPValid"
      is HealthConnect -> "isHealthConnectValid"
    }

  companion object {
    /**
     * Gets all available integration providers.
     */
    fun getAllProviders(): List<IntegrationProvider> =
      listOf(
        Fitbit,
        MyFitnessPal,
        HealthConnect,
      )

    /**
     * Gets providers that require OAuth flow.
     */
    fun getOAuthProviders(): List<IntegrationProvider> =
      listOf(
        Fitbit,
        MyFitnessPal,
      )

    /**
     * Gets platform-specific providers.
     */
    fun getPlatformSpecificProviders(): List<IntegrationProvider> =
      listOf(
        HealthConnect,
      )

    /**
     * Finds a provider by its API value.
     */
    fun fromApiValue(apiValue: String): IntegrationProvider? =
      when (apiValue) {
        "fitbit" -> Fitbit
        "mfPal" -> MyFitnessPal
        "healthConnect" -> HealthConnect
        else -> null
      }

    /**
     * Finds a provider by its display name.
     */
    fun fromDisplayName(displayName: String): IntegrationProvider? =
      when (displayName) {
        "Fitbit" -> Fitbit
        "MyFitnessPal" -> MyFitnessPal
        "Health Connect" -> HealthConnect
        else -> null
      }

    /**
     * Finds a provider by its integration flag key.
     */
    fun fromIntegrationFlagKey(flagKey: String): IntegrationProvider? =
      when (flagKey) {
        "isFitbitOn" -> Fitbit
        "isMFPOn" -> MyFitnessPal
        "isHealthConnectOn" -> HealthConnect
        else -> null
      }
  }

  override fun toString(): String = apiValue
}
