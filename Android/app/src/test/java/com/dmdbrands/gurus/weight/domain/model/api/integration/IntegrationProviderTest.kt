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
}
