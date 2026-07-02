package com.dmdbrands.gurus.weight.domain.model.api.integration

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Unit tests for [IntegrationProvider.getCategory] — the section a provider renders under
 * on the device-driven Integrations screen.
 */
class IntegrationProviderTest {

    @Test
    fun `Fitbit is categorized under WeightScale`() {
        assertThat(IntegrationProvider.Fitbit.getCategory())
            .isEqualTo(IntegrationCategory.WeightScale)
    }

    @Test
    fun `MyFitnessPal is categorized under WeightScale`() {
        assertThat(IntegrationProvider.MyFitnessPal.getCategory())
            .isEqualTo(IntegrationCategory.WeightScale)
    }

    @Test
    fun `HealthConnect is categorized under WeightScaleAndBpm`() {
        assertThat(IntegrationProvider.HealthConnect.getCategory())
            .isEqualTo(IntegrationCategory.WeightScaleAndBpm)
    }

    // -------------------------------------------------------------------------
    // getOAuthUrl
    // -------------------------------------------------------------------------

    @Test
    fun `getOAuthUrl returns Fitbit url containing account state`() {
        val url = IntegrationProvider.Fitbit.getOAuthUrl("acc-123")
        assertThat(url).contains("fitbit.com")
        assertThat(url).contains("state=v3-acc-123")
    }

    @Test
    fun `getOAuthUrl returns MyFitnessPal url containing account state`() {
        val url = IntegrationProvider.MyFitnessPal.getOAuthUrl("acc-123")
        assertThat(url).contains("myfitnesspal.com")
        assertThat(url).contains("state=v3-acc-123")
    }

    @Test
    fun `getOAuthUrl returns null for HealthConnect`() {
        assertThat(IntegrationProvider.HealthConnect.getOAuthUrl("acc-123")).isNull()
    }

    // -------------------------------------------------------------------------
    // requiresOAuth / isPlatformSpecific / getPlatformRequirement
    // -------------------------------------------------------------------------

    @Test
    fun `requiresOAuth is true for OAuth providers and false for HealthConnect`() {
        assertThat(IntegrationProvider.Fitbit.requiresOAuth()).isTrue()
        assertThat(IntegrationProvider.MyFitnessPal.requiresOAuth()).isTrue()
        assertThat(IntegrationProvider.HealthConnect.requiresOAuth()).isFalse()
    }

    @Test
    fun `isPlatformSpecific is true only for HealthConnect`() {
        assertThat(IntegrationProvider.HealthConnect.isPlatformSpecific()).isTrue()
        assertThat(IntegrationProvider.Fitbit.isPlatformSpecific()).isFalse()
        assertThat(IntegrationProvider.MyFitnessPal.isPlatformSpecific()).isFalse()
    }

    @Test
    fun `getPlatformRequirement returns Android requirement for HealthConnect and null otherwise`() {
        assertThat(IntegrationProvider.HealthConnect.getPlatformRequirement()).isEqualTo("Android 13+")
        assertThat(IntegrationProvider.Fitbit.getPlatformRequirement()).isNull()
        assertThat(IntegrationProvider.MyFitnessPal.getPlatformRequirement()).isNull()
    }

    // -------------------------------------------------------------------------
    // getApiEndpoint / flag keys / toString
    // -------------------------------------------------------------------------

    @Test
    fun `getApiEndpoint maps each provider`() {
        assertThat(IntegrationProvider.Fitbit.getApiEndpoint()).isEqualTo("fitbit")
        assertThat(IntegrationProvider.MyFitnessPal.getApiEndpoint()).isEqualTo("mfp")
        assertThat(IntegrationProvider.HealthConnect.getApiEndpoint()).isEqualTo("healthconnect")
    }

    @Test
    fun `getIntegrationFlagKey maps each provider`() {
        assertThat(IntegrationProvider.Fitbit.getIntegrationFlagKey()).isEqualTo("isFitbitOn")
        assertThat(IntegrationProvider.MyFitnessPal.getIntegrationFlagKey()).isEqualTo("isMFPOn")
        assertThat(IntegrationProvider.HealthConnect.getIntegrationFlagKey()).isEqualTo("isHealthConnectOn")
    }

    @Test
    fun `getValidityFlagKey maps each provider`() {
        assertThat(IntegrationProvider.Fitbit.getValidityFlagKey()).isEqualTo("isFitbitValid")
        assertThat(IntegrationProvider.MyFitnessPal.getValidityFlagKey()).isEqualTo("isMFPValid")
        assertThat(IntegrationProvider.HealthConnect.getValidityFlagKey()).isEqualTo("isHealthConnectValid")
    }

    @Test
    fun `toString returns apiValue`() {
        assertThat(IntegrationProvider.Fitbit.toString()).isEqualTo("fitbit")
        assertThat(IntegrationProvider.MyFitnessPal.toString()).isEqualTo("mfPal")
        assertThat(IntegrationProvider.HealthConnect.toString()).isEqualTo("healthConnect")
    }

    // -------------------------------------------------------------------------
    // companion lookups
    // -------------------------------------------------------------------------

    @Test
    fun `getAllProviders returns the three providers`() {
        assertThat(IntegrationProvider.getAllProviders()).containsExactly(
            IntegrationProvider.Fitbit,
            IntegrationProvider.MyFitnessPal,
            IntegrationProvider.HealthConnect,
        )
    }

    @Test
    fun `getOAuthProviders returns only OAuth providers`() {
        assertThat(IntegrationProvider.getOAuthProviders()).containsExactly(
            IntegrationProvider.Fitbit,
            IntegrationProvider.MyFitnessPal,
        )
    }

    @Test
    fun `getPlatformSpecificProviders returns only HealthConnect`() {
        assertThat(IntegrationProvider.getPlatformSpecificProviders())
            .containsExactly(IntegrationProvider.HealthConnect)
    }

    @Test
    fun `fromApiValue maps known values and returns null otherwise`() {
        assertThat(IntegrationProvider.fromApiValue("fitbit")).isEqualTo(IntegrationProvider.Fitbit)
        assertThat(IntegrationProvider.fromApiValue("mfPal")).isEqualTo(IntegrationProvider.MyFitnessPal)
        assertThat(IntegrationProvider.fromApiValue("healthConnect")).isEqualTo(IntegrationProvider.HealthConnect)
        assertThat(IntegrationProvider.fromApiValue("unknown")).isNull()
    }

    @Test
    fun `fromDisplayName maps known names and returns null otherwise`() {
        assertThat(IntegrationProvider.fromDisplayName("Fitbit")).isEqualTo(IntegrationProvider.Fitbit)
        assertThat(IntegrationProvider.fromDisplayName("MyFitnessPal")).isEqualTo(IntegrationProvider.MyFitnessPal)
        assertThat(IntegrationProvider.fromDisplayName("Health Connect")).isEqualTo(IntegrationProvider.HealthConnect)
        assertThat(IntegrationProvider.fromDisplayName("Nope")).isNull()
    }

    @Test
    fun `fromIntegrationFlagKey maps known keys and returns null otherwise`() {
        assertThat(IntegrationProvider.fromIntegrationFlagKey("isFitbitOn")).isEqualTo(IntegrationProvider.Fitbit)
        assertThat(IntegrationProvider.fromIntegrationFlagKey("isMFPOn")).isEqualTo(IntegrationProvider.MyFitnessPal)
        assertThat(IntegrationProvider.fromIntegrationFlagKey("isHealthConnectOn")).isEqualTo(IntegrationProvider.HealthConnect)
        assertThat(IntegrationProvider.fromIntegrationFlagKey("isUnknown")).isNull()
    }
}
