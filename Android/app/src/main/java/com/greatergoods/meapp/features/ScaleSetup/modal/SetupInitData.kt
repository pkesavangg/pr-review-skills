package com.greatergoods.meapp.features.ScaleSetup.modal

import com.greatergoods.meapp.features.ScaleSetup.enums.ScaleSetupStep

data class SetupInitData<Step : ScaleSetupStep>(
  val sku: String,
  val initialStep: Step,
  val broadcastId: String? = null
)
