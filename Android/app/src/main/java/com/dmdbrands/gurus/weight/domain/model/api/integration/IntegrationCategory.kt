package com.dmdbrands.gurus.weight.domain.model.api.integration

/**
 * Section the Integrations screen renders a provider under.
 * Driven by the kind of paired device that can use the integration, not by product type.
 */
enum class IntegrationCategory {
  WeightScale,
  WeightScaleAndBpm,
}
