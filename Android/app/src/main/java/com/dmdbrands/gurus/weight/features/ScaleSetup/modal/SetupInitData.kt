package com.dmdbrands.gurus.weight.features.ScaleSetup.modal

import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.ScaleSetupStep

data class SetupInitData<Step : ScaleSetupStep>(
  val sku: String,
  val initialStep: Step,
  val broadcastId: String? = null
)
