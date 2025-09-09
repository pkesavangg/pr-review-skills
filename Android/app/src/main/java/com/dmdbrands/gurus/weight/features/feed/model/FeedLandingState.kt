package com.dmdbrands.gurus.weight.features.feed.model

import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.greatergoods.ggInAppMessaging.domain.models.FeedItem

/**
 * State for Feed Landing screen
 */
data class FeedLandingState(
  val feedItem: FeedItem? = null,
  val isLoading: Boolean = true,
  val error: String? = null,
  val promoCodeCopied: Boolean = false,
  val lastAction: String? = null
) : IReducer.State

/**
 * Intents for Feed Landing screen actions
 */
sealed class FeedLandingIntent : IReducer.Intent {
  /** Set the feed item to display */
  data class SetFeedItem(val feedItem: FeedItem) : FeedLandingIntent()

  /** Set loading state */
  data class SetLoading(val isLoading: Boolean) : FeedLandingIntent()

  /** Set error state */
  data class SetError(val error: String?) : FeedLandingIntent()

  /** Handle promo code click */
  data class OnPromoCodeClick(val promoCode: String) : FeedLandingIntent()

  /** Handle shop now click */
  data class OnShopNowClick(val link: String?) : FeedLandingIntent()

  /** Handle product click */
  data class OnProductClick(val link: String, val variationId: Int?) : FeedLandingIntent()

  /** Handle help click */
  object OnHelpClick : FeedLandingIntent()

  /** Retry loading */
  object Retry : FeedLandingIntent()

  /** Clear error */
  object ClearError : FeedLandingIntent()

  object OpenFAQ : FeedLandingIntent()
}

/**
 * Reducer for Feed Landing screen state transitions
 */
class FeedLandingReducer : IReducer<FeedLandingState, FeedLandingIntent> {
  override fun reduce(
    state: FeedLandingState,
    intent: FeedLandingIntent
  ): FeedLandingState = when (intent) {
    is FeedLandingIntent.SetFeedItem -> {
      state.copy(
        feedItem = intent.feedItem,
        isLoading = false,
        error = null,
      )
    }

    is FeedLandingIntent.SetLoading -> {
      state.copy(isLoading = intent.isLoading)
    }

    is FeedLandingIntent.SetError -> {
      state.copy(
        isLoading = false,
        error = intent.error,
      )
    }

    is FeedLandingIntent.OnPromoCodeClick -> {
      state.copy(
        promoCodeCopied = true,
        lastAction = "Promo code copied: ${intent.promoCode}",
      )
    }

    is FeedLandingIntent.OnShopNowClick -> {
      state.copy(
        lastAction = "Shop now clicked: ${intent.link ?: "No link"}",
      )
    }

    is FeedLandingIntent.OnProductClick -> {
      state.copy(
        lastAction = "Product clicked: ${intent.link}, variation: ${intent.variationId}",
      )
    }

    is FeedLandingIntent.OnHelpClick -> {
      state.copy(
        lastAction = "Help clicked",
      )
    }

    is FeedLandingIntent.Retry -> {
      state.copy(
        isLoading = true,
        error = null,
      )
    }

    is FeedLandingIntent.ClearError -> {
      state.copy(error = null)
    }

    FeedLandingIntent.OpenFAQ -> {
      state.copy(
        lastAction = "FAQ clicked",
      )
    }
  }
}
