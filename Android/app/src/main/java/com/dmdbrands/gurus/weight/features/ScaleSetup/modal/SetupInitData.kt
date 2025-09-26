package com.dmdbrands.gurus.weight.features.ScaleSetup.modal

import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.ScaleSetupStep
import com.dmdbrands.gurus.weight.features.common.model.ScaleInfo

data class SetupInitData<Step : ScaleSetupStep>(
  val sku: String,
  val initialStep: Step,
  val scaleInfo: ScaleInfo? = null,
  val broadcastId: String? = null
)
