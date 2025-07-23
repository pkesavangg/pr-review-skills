package com.greatergoods.meapp.features.ScaleSetup.modal

data class SetupInitData<Step>(
  val sku: String,
  val initialStep: Step,
  val broadcastId: String? = null
)
