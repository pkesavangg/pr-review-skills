package com.dmdbrands.gurus.weight.features.DeviceSetup.modal

import java.util.UUID

/**
 * In-memory data class for baby profile during setup.
 * Persistence is handled in a separate ticket.
 */
data class BabyProfile(
  val id: String = UUID.randomUUID().toString(),
  val name: String = "",
  val birthday: String? = null,
  val biologicalSex: String? = null,
  val birthLength: String? = null,
  val birthWeight: String? = null,
)
