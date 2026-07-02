package com.dmdbrands.gurus.weight.features.DeviceSetup.modal

import com.dmdbrands.gurus.weight.features.DeviceSetup.enums.DeviceSetupStep
import com.dmdbrands.gurus.weight.features.common.model.DeviceModelInfo

data class SetupInitData<Step : DeviceSetupStep>(
  val sku: String,
  val initialStep: Step,
  val scaleInfo: DeviceModelInfo? = null,
  val broadcastId: String? = null
)
