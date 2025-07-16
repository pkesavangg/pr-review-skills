package com.greatergoods.meapp.features.scaleDetails.reducer

import com.greatergoods.meapp.domain.interfaces.IReducer
import com.greatergoods.meapp.domain.model.storage.Device

/**
 * State for ScaleDetailsScreen.
 */
data class ScaleDetailsState(
  val scale: Device? = null,
) : IReducer.State

/**
 * Intents for ScaleDetailsScreen actions.
 */
sealed interface ScaleDetailsIntent : IReducer.Intent {
  data class SetScaleInfo(
    val scale: Device,
  ) : ScaleDetailsIntent

  object EditName : ScaleDetailsIntent

  object DeleteScale : ScaleDetailsIntent

  object OpenProductGuide : ScaleDetailsIntent

  object OpenScaleMode : ScaleDetailsIntent

  object OpenScaleUsers : ScaleDetailsIntent
  object OpenScaleDisplayMetrics : ScaleDetailsIntent

  object Back : ScaleDetailsIntent
}

/**
 * Reducer for ScaleDetailsScreen.
 */
class ScaleDetailsReducer : IReducer<ScaleDetailsState, ScaleDetailsIntent> {
  override fun reduce(
    state: ScaleDetailsState,
    intent: ScaleDetailsIntent,
  ): ScaleDetailsState? =
    when (intent) {
      is ScaleDetailsIntent.SetScaleInfo -> state.copy(scale = intent.scale)
      ScaleDetailsIntent.EditName -> state.copy()
      ScaleDetailsIntent.DeleteScale -> state.copy()
      ScaleDetailsIntent.OpenProductGuide -> state.copy()
      ScaleDetailsIntent.Back -> state.copy()
      else -> state.copy()
    }
}
